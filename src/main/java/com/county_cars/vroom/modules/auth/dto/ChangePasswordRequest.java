package com.county_cars.vroom.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for the change-password endpoint (authenticated users only).
 *
 * <p>Validation is intentional at DTO level so the controller returns a clean
 * 400 before any Keycloak call is made.</p>
 */
@Data
@Schema(description = "Request to change the authenticated user's password")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(description = "The user's current (existing) password", example = "OldP@ss123")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "New password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    @Schema(description = "The new password (min 8 chars, upper+lower+digit+special)", example = "NewP@ss456")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Schema(description = "Must match newPassword exactly", example = "NewP@ss456")
    private String confirmNewPassword;
}

