package com.county_cars.vroom.modules.verification.dto.request;

import com.county_cars.vroom.modules.verification.entity.VerificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request to submit a verification request")
public class CreateVerificationRequest {

    @NotNull
    @Schema(description = "Type of verification")
    private VerificationType verificationType;

    @Schema(description = "Optional notes from the user")
    private String notes;

    @Schema(description = "Optional expiry date for time-limited verifications")
    private LocalDateTime expiresAt;
}

