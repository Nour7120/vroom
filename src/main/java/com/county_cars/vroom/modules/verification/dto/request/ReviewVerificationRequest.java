package com.county_cars.vroom.modules.verification.dto.request;

import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to review (approve/reject) a verification")
public class ReviewVerificationRequest {

    @NotNull
    @Schema(description = "New status: APPROVED or REJECTED")
    private VerificationStatus status;

    @Schema(description = "Reviewer notes")
    private String notes;
}

