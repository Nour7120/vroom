package com.county_cars.vroom.modules.user_profile.service.impl;

import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
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
        findById(userProfileId); // validate existence
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

