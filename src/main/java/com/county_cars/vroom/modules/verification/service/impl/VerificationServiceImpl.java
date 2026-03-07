package com.county_cars.vroom.modules.verification.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import com.county_cars.vroom.modules.verification.dto.request.CreateVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.request.ReviewVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.response.VerificationResponse;
import com.county_cars.vroom.modules.verification.entity.VerificationRequest;
import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import com.county_cars.vroom.modules.verification.mapper.VerificationMapper;
import com.county_cars.vroom.modules.verification.repository.VerificationRequestRepository;
import com.county_cars.vroom.modules.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationServiceImpl implements VerificationService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final UserProfileRepository userProfileRepository;
    private final VerificationMapper verificationMapper;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public VerificationResponse createVerificationRequest(Long userProfileId, CreateVerificationRequest request) {
        // Prevent duplicate pending request of same type
        if (verificationRequestRepository.existsByUserProfileIdAndVerificationTypeAndStatusAndIsDeletedFalse(
                userProfileId, request.getVerificationType(), VerificationStatus.PENDING)) {
            throw new BadRequestException("A pending verification of this type already exists");
        }
        UserProfile userProfile = userProfileRepository.findById(userProfileId)
                .orElseThrow(() -> new NotFoundException("UserProfile not found: " + userProfileId));

        VerificationRequest entity = verificationMapper.toEntity(request);
        entity.setUserProfile(userProfile);
        entity.setStatus(VerificationStatus.PENDING);
        return verificationMapper.toResponse(verificationRequestRepository.save(entity));
    }

    @Override
    public VerificationResponse getById(Long id) {
        return verificationMapper.toResponse(findById(id));
    }

    @Override
    public Page<VerificationResponse> getByUserProfile(Long userProfileId, Pageable pageable) {
        return verificationRequestRepository
                .findAllByUserProfileIdAndIsDeletedFalse(userProfileId, pageable)
                .map(verificationMapper::toResponse);
    }

    @Override
    public Page<VerificationResponse> getByStatus(VerificationStatus status, Pageable pageable) {
        return verificationRequestRepository
                .findAllByStatusAndIsDeletedFalse(status, pageable)
                .map(verificationMapper::toResponse);
    }

    @Override
    @Transactional
    public VerificationResponse review(Long id, ReviewVerificationRequest request) {
        VerificationRequest entity = findById(id);
        if (entity.getStatus() != VerificationStatus.PENDING && entity.getStatus() != VerificationStatus.IN_REVIEW) {
            throw new BadRequestException("Cannot review a verification in status: " + entity.getStatus());
        }
        entity.setStatus(request.getStatus());
        entity.setNotes(request.getNotes());
        entity.setReviewedBy(currentUserService.getCurrentKeycloakUserId());
        entity.setReviewedAt(LocalDateTime.now());
        return verificationMapper.toResponse(verificationRequestRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteVerificationRequest(Long id) {
        VerificationRequest entity = findById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        verificationRequestRepository.save(entity);
    }

    private VerificationRequest findById(Long id) {
        return verificationRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("VerificationRequest not found: " + id));
    }
}

