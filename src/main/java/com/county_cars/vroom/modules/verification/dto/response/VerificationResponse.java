package com.county_cars.vroom.modules.verification.dto.response;

import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import com.county_cars.vroom.modules.verification.entity.VerificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Verification request response")
public class VerificationResponse {
    private Long id;
    private Long userProfileId;
    private VerificationType verificationType;
    private VerificationStatus status;
    private String notes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

