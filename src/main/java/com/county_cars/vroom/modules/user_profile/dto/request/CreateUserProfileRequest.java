package com.county_cars.vroom.modules.user_profile.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to create a new user profile")
public class CreateUserProfileRequest {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "Keycloak subject UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String keycloakUserId;

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "User's email address", example = "user@example.com")
    private String email;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "First name", example = "John")
    private String firstName;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Size(max = 32)
    @Schema(description = "Phone number", example = "+1234567890")
    private String phoneNumber;

    @Size(max = 1024)
    @Schema(description = "Avatar URL")
    private String avatarUrl;
}

