package com.county_cars.vroom.modules.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Complete third-party user registration request")
public class CompleteRegistrationRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 128, message = "Display name must be between 2 and 128 characters")
    @Schema(description = "Display Name", example = "Nour71")
    private String displayName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255)
    @Schema(description = "Email address — used as Keycloak username", example = "nour.amr@gmail.com")
    private String email;

    @Size(max = 32)
    @Schema(description = "Phone number", example = "+201234567890")
    private String phoneNumber;
}
