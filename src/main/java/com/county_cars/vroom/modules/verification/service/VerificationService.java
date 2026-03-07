package com.county_cars.vroom.modules.verification.service;

import com.county_cars.vroom.modules.verification.dto.request.CreateVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.request.ReviewVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.response.VerificationResponse;
import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VerificationService {
    VerificationResponse createVerificationRequest(Long userProfileId, CreateVerificationRequest request);
    VerificationResponse getById(Long id);
    Page<VerificationResponse> getByUserProfile(Long userProfileId, Pageable pageable);
    Page<VerificationResponse> getByStatus(VerificationStatus status, Pageable pageable);
    VerificationResponse review(Long id, ReviewVerificationRequest request);
    void deleteVerificationRequest(Long id);
}

