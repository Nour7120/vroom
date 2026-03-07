package com.county_cars.vroom.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for the forgot-password endpoint.
 *
 * <p>The email is normalized (lowercase + trim) in the service layer.</p>
 */
@Data
@Schema(description = "Request to trigger a Keycloak password-reset email")
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Schema(description = "Email address of the account", example = "nour.amr@gmail.com")
    private String email;
}

