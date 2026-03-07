package com.county_cars.vroom.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response returned by {@code GET /api/v1/auth/2fa/status}.
 */
@Data
@AllArgsConstructor
@Schema(description = "Current 2FA status for the authenticated user")
public class TwoFactorStatusResponse {

    @Schema(description = "true if TOTP is active (an OTP credential exists in Keycloak)", example = "true")
    private boolean enabled;
}

