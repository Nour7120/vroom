package com.county_cars.vroom.modules.registration.dto;

import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Returned to the client after successful registration.
 * Does NOT include sensitive data (no password, no full keycloakId in a production build).
 */
@Data
@Builder
@Schema(description = "Registration response returned after a successful sign-up")
public class RegistrationResponse {

    @Schema(description = "Internal database user profile ID", example = "42")
    private Long userProfileId;

    @Schema(description = "Keycloak UUID assigned to the new user")
    private String keycloakUserId;

    @Schema(description = "Registered email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Display name", example = "JohnD")
    private String displayName;

    @Schema(description = "Account status after registration", example = "PENDING_VERIFICATION")
    private UserStatus status;

    @Schema(
        description = "Human-readable message describing next steps",
        example = "Registration successful. A verification email has been sent to john.doe@example.com."
    )
    private String message;
}

