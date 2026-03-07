package com.county_cars.vroom.modules.auth.service;

import com.county_cars.vroom.modules.auth.dto.TwoFactorStatusResponse;

/**
 * Service handling TOTP-based Two-Factor Authentication (2FA) for the current user.
 *
 * <p>All methods resolve the caller identity exclusively from the JWT via
 * {@link com.county_cars.vroom.modules.keycloak.CurrentUserService} —
 * no userId parameter is accepted.</p>
 */
public interface Auth2FAService {

    /**
     * Enables 2FA for the currently authenticated user.
     *
     * <p>Adds {@code "CONFIGURE_TOTP"} to the user's Keycloak required actions
     * if not already present (idempotent). Keycloak will prompt the user to
     * configure an OTP device on next login.</p>
     *
     * @throws IllegalStateException if the Keycloak Admin API call fails
     */
    void enable();

    /**
     * Disables 2FA for the currently authenticated user.
     *
     * <p>Removes all {@code otp}-type credentials from Keycloak and strips
     * {@code "CONFIGURE_TOTP"} from required actions.</p>
     *
     * @throws IllegalStateException if the Keycloak Admin API call fails
     */
    void disable();

    /**
     * Returns the current 2FA status of the authenticated user.
     *
     * <p>A user has 2FA enabled if at least one {@code otp} credential exists in Keycloak.</p>
     *
     * @return {@link TwoFactorStatusResponse} with {@code enabled} flag
     */
    TwoFactorStatusResponse getStatus();
}

