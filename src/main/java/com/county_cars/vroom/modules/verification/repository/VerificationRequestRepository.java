package com.county_cars.vroom.modules.verification.repository;

import com.county_cars.vroom.modules.verification.entity.VerificationRequest;
import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import com.county_cars.vroom.modules.verification.entity.VerificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {
    Page<VerificationRequest> findAllByUserProfileIdAndIsDeletedFalse(Long userProfileId, Pageable pageable);
    Page<VerificationRequest> findAllByStatusAndIsDeletedFalse(VerificationStatus status, Pageable pageable);
    Page<VerificationRequest> findAllByVerificationTypeAndIsDeletedFalse(VerificationType type, Pageable pageable);
    boolean existsByUserProfileIdAndVerificationTypeAndStatusAndIsDeletedFalse(
            Long userProfileId, VerificationType type, VerificationStatus status);
}

