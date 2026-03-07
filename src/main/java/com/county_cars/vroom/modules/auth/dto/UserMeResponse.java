package com.county_cars.vroom.modules.auth.dto;

import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Response returned by {@code GET /api/v1/auth/me}.
 *
 * <p>All business data comes from the internal database ({@code user_profile} table).
 * Only three minimal pieces of data are enriched from Keycloak:
 * <ul>
 *   <li>{@code emailVerified} — from {@code UserRepresentation.isEmailVerified()}</li>
 *   <li>{@code authProviders} — identity provider aliases (e.g. {@code google}, {@code apple})</li>
 *   <li>{@code hasLocalPassword} — whether a {@code password} credential type exists in Keycloak</li>
 * </ul>
 * </p>
 *
 * <p><b>Login-method classification:</b>
 * <ul>
 *   <li>Local only       → {@code authProviders} is empty AND {@code hasLocalPassword} is {@code true}</li>
 *   <li>Social only      → {@code authProviders} is non-empty AND {@code hasLocalPassword} is {@code false}</li>
 *   <li>Mixed            → both present</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@Schema(description = "Full profile of the currently authenticated user")
public class UserMeResponse {

    // ── Internal DB fields ────────────────────────────────────────────────────────

    @Schema(description = "Internal database user profile ID", example = "42")
    private Long id;

    @Schema(description = "Keycloak UUID — matches the JWT sub claim", example = "a3f1c2d4-...")
    private String keycloakUserId;

    @Schema(description = "Email address", example = "nour.amr@gmail.com")
    private String email;

    @Schema(description = "Display name", example = "Nour71")
    private String displayName;

    @Schema(description = "Phone number", example = "+201234567890")
    private String phoneNumber;

    @Schema(description = "Avatar URL", example = "https://cdn.example.com/avatars/42.jpg")
    private String avatarUrl;

    @Schema(description = "Account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Timestamp when the profile was created")
    private LocalDateTime createdAt;

    // ── Keycloak-enriched fields ──────────────────────────────────────────────────

    @Schema(description = "Whether the user has verified their email via Keycloak", example = "true")
    private boolean emailVerified;

    @Schema(
        description = "Set of federated identity provider aliases linked to this account. " +
                      "Empty if the user only uses a local password.",
        example = "[\"google\", \"apple\"]"
    )
    private Set<String> authProviders;

    @Schema(
        description = "Whether the user has a local Keycloak password credential. " +
                      "False for social-only accounts.",
        example = "true"
    )
    private boolean hasLocalPassword;
}

