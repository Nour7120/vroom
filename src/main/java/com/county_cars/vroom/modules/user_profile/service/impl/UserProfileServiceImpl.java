package com.county_cars.vroom.modules.user_profile.service.impl;

import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.user_profile.dto.request.CreateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UpdateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UserLocationRequest;
import com.county_cars.vroom.modules.user_profile.dto.response.UserLocationResponse;
import com.county_cars.vroom.modules.user_profile.dto.response.UserProfileResponse;
import com.county_cars.vroom.modules.user_profile.entity.UserLocation;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.mapper.UserProfileMapper;
import com.county_cars.vroom.modules.user_profile.repository.UserLocationRepository;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import com.county_cars.vroom.modules.user_profile.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserLocationRepository userLocationRepository;
    private final UserProfileMapper userProfileMapper;
    private final CurrentUserService currentUserService;
    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;

    // ─── UserProfile CRUD ────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserProfileResponse createUserProfile(CreateUserProfileRequest request) {
        if (userProfileRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        if (userProfileRepository.existsByKeycloakUserId(request.getKeycloakUserId())) {
            throw new ConflictException("Keycloak user already has a profile: " + request.getKeycloakUserId());
        }
        UserProfile entity = userProfileMapper.toEntity(request);
        return userProfileMapper.toResponse(userProfileRepository.save(entity));
    }

    @Override
    public UserProfileResponse getUserProfileById(Long id) {
        return userProfileMapper.toResponse(findById(id));
    }

    @Override
    public UserProfileResponse getMyProfile() {
        return userProfileMapper.toResponse(currentUserService.getCurrentUserProfile());
    }

    @Override
    public Page<UserProfileResponse> getAllUserProfiles(Pageable pageable) {
        return userProfileRepository.findAllByIsDeletedFalse(pageable)
                .map(userProfileMapper::toResponse);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserProfile(Long id, UpdateUserProfileRequest request) {
        UserProfile entity = findById(id);
        userProfileMapper.updateEntityFromRequest(request, entity);
        return userProfileMapper.toResponse(userProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteUserProfile(Long id) {
        UserProfile entity = findById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        userProfileRepository.save(entity);
    }

    // ─── Avatar upload (calling-module orchestration) ─────────────────────────

    /**
     * Uploads an image, links it to the profile and (on success) deletes the old avatar.
     *
     * <p>Orchestration flow:
     * <ol>
     *   <li>{@code attachmentService.upload} — REQUIRES_NEW, commits immediately.</li>
     *   <li>Fetch the saved {@link Attachment} entity in the outer transaction.</li>
     *   <li>Update {@link UserProfile#setAvatarAttachmentId} and
     *       {@link UserProfile#setAvatarUrl} in the outer transaction.</li>
     *   <li>{@code markAsLinked} — participates in the outer transaction.</li>
     *   <li>On any failure: {@code deleteOrphan} — REQUIRES_NEW, commits cleanup.</li>
     *   <li>After successful commit: soft-delete the old avatar attachment.</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file) {
        UserProfile profile   = currentUserService.getCurrentUserProfile();
        Long oldAttachmentId  = profile.getAvatarAttachmentId();

        // ── Step 1: Upload (REQUIRES_NEW — commits independently) ──────────────
        AttachmentResponse attResp = attachmentService.upload(file, AttachmentVisibility.PUBLIC);

        try {
            // ── Step 2: Fetch entity in the outer transaction ───────────────────
            Attachment attachment = attachmentRepository.findById(attResp.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Attachment not found after upload: " + attResp.getId()));

            // ── Step 3: Update profile ──────────────────────────────────────────
            profile.setAvatarAttachmentId(attachment.getId());
            profile.setAvatarUrl("/api/v1/attachments/" + attachment.getId());
            userProfileRepository.save(profile);

            // ── Step 4: Mark as LINKED (participates in outer tx) ───────────────
            attachmentService.markAsLinked(attachment.getId());

            log.info("Avatar updated: profileId={} attachmentId={}", profile.getId(), attachment.getId());

        } catch (Exception e) {
            // ── Step 5: Cleanup on failure (REQUIRES_NEW — commits independently) ─
            attachmentService.deleteOrphan(attResp.getId());
            log.warn("Avatar upload failed; orphan attachment cleaned up: id={}", attResp.getId());
            throw e;
        }

        // ── Step 6: Delete the old avatar after the new one is safely committed ─
        if (oldAttachmentId != null) {
            attachmentService.deleteBySystem(oldAttachmentId);
            log.info("Old avatar attachment deleted: id={}", oldAttachmentId);
        }

        return userProfileMapper.toResponse(userProfileRepository.findById(profile.getId())
                .orElseThrow(() -> new NotFoundException("UserProfile not found: " + profile.getId())));
    }

    // ─── UserLocation CRUD ───────────────────────────────────────────────────

    @Override
    @Transactional
    public UserLocationResponse addLocation(Long userProfileId, UserLocationRequest request) {
        UserProfile profile = findById(userProfileId);
        UserLocation location = userProfileMapper.toLocationEntity(request);
        location.setUserProfile(profile);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearPrimaryLocations(userProfileId);
        }
        return userProfileMapper.toLocationResponse(userLocationRepository.save(location));
    }

    @Override
    public List<UserLocationResponse> getLocations(Long userProfileId) {
        findById(userProfileId);
        return userLocationRepository.findAllByUserProfileIdAndIsDeletedFalse(userProfileId)
                .stream().map(userProfileMapper::toLocationResponse).toList();
    }

    @Override
    @Transactional
    public UserLocationResponse updateLocation(Long userProfileId, Long locationId, UserLocationRequest request) {
        UserLocation location = userLocationRepository
                .findByIdAndUserProfileIdAndIsDeletedFalse(locationId, userProfileId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearPrimaryLocations(userProfileId);
        }
        userProfileMapper.updateLocationFromRequest(request, location);
        return userProfileMapper.toLocationResponse(userLocationRepository.save(location));
    }

    @Override
    @Transactional
    public void deleteLocation(Long userProfileId, Long locationId) {
        UserLocation location = userLocationRepository
                .findByIdAndUserProfileIdAndIsDeletedFalse(locationId, userProfileId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        location.setIsDeleted(Boolean.TRUE);
        location.setDeletedAt(LocalDateTime.now());
        location.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        userLocationRepository.save(location);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserProfile findById(Long id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("UserProfile not found: " + id));
    }

    private void clearPrimaryLocations(Long userProfileId) {
        userLocationRepository.findAllByUserProfileIdAndIsDeletedFalse(userProfileId)
                .forEach(l -> l.setIsPrimary(Boolean.FALSE));
    }
}

