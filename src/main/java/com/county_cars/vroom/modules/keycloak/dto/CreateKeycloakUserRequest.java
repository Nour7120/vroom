package com.county_cars.vroom.modules.keycloak.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Internal DTO passed to {@code KeycloakAdminService#createUser}.
 * Never exposed directly to the API layer.
 */
@Data
@Builder
public class CreateKeycloakUserRequest {
    private String email;
    private String displayName;
    private String firstName;
    private String lastName;
    private String password;
    /** When true Keycloak sends its built-in verification email immediately after creation. */
    @Builder.Default
    private boolean sendVerificationEmail = true;
    /** When true the account is enabled right away (normal registration flow). */
    @Builder.Default
    private boolean enabled = true;
}

