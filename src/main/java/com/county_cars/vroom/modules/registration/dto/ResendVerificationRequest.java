package com.county_cars.vroom.modules.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for the resend-verification-email endpoint.
 */
@Data
@Schema(description = "Request to re-send the Keycloak email verification link")
public class ResendVerificationRequest {

    @NotBlank
    @Email
    @Schema(description = "Email of the account to re-verify", example = "john.doe@example.com")
    private String email;
}

