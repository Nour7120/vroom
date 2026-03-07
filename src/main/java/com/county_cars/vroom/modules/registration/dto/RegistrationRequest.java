package com.county_cars.vroom.modules.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Public-facing registration request submitted by mobile / web clients.
 */
@Data
@Schema(description = "New user registration request")
public class RegistrationRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 128, message = "Display name must be between 2 and 128 characters")
    @Schema(description = "Display Name", example = "Nour71")
    private String displayName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255)
    @Schema(description = "Email address — used as Keycloak username", example = "nour.amr@gmail.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    @Schema(description = "Password (min 8 chars, upper+lower+digit+special)", example = "MyP@ss123")
    private String password;

    @Size(max = 32)
    @Schema(description = "Phone number", example = "+201234567890")
    private String phoneNumber;
}

