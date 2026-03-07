package com.county_cars.vroom.modules.auth.service.impl;

import com.county_cars.vroom.modules.auth.dto.TwoFactorStatusResponse;
import com.county_cars.vroom.modules.auth.service.Auth2FAService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements TOTP-based 2FA operations for the currently authenticated user.
 *
 * <p>Identity is always resolved from the JWT via {@link CurrentUserService} —
 * no userId is accepted from the caller.</p>
 *
 * <p>All Keycloak interactions are delegated to {@link KeycloakAdminService}.
 * Errors thrown by Keycloak propagate as {@link IllegalStateException}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Auth2FAServiceImpl implements Auth2FAService {

    private final KeycloakAdminService keycloakAdminService;
    private final CurrentUserService currentUserService;

    // ─── Enable 2FA ──────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Adds {@code CONFIGURE_TOTP} to the user's Keycloak required actions.
     * The operation is idempotent: if the action is already present, no update is made.</p>
     */
    @Override
    @Transactional
    public void enable() {
        String keycloakUserId = currentUserService.getCurrentKeycloakUserId();
        log.info("2FA enable requested for keycloakUserId={}", keycloakUserId);
        keycloakAdminService.enableTwoFactor(keycloakUserId);
        log.info("2FA enable completed for keycloakUserId={}", keycloakUserId);
    }

    // ─── Disable 2FA ─────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Removes all OTP credentials and strips {@code CONFIGURE_TOTP} from required actions.</p>
     */
    @Override
    @Transactional
    public void disable() {
        String keycloakUserId = currentUserService.getCurrentKeycloakUserId();
        log.info("2FA disable requested for keycloakUserId={}", keycloakUserId);
        keycloakAdminService.disableTwoFactor(keycloakUserId);
        log.info("2FA disable completed for keycloakUserId={}", keycloakUserId);
    }

    // ─── Status ──────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>{@code enabled} is {@code true} only when an {@code otp} credential
     * already exists in Keycloak (setup completed), not merely when
     * {@code CONFIGURE_TOTP} is pending.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus() {
        String keycloakUserId = currentUserService.getCurrentKeycloakUserId();
        log.debug("2FA status requested for keycloakUserId={}", keycloakUserId);
        boolean enabled = keycloakAdminService.isTwoFactorEnabled(keycloakUserId);
        log.debug("2FA status for keycloakUserId={}: enabled={}", keycloakUserId, enabled);
        return new TwoFactorStatusResponse(enabled);
    }
}

