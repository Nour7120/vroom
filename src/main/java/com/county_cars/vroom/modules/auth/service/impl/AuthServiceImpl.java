package com.county_cars.vroom.modules.auth.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordResponse;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordResponse;
import com.county_cars.vroom.modules.auth.dto.UserMeResponse;
import com.county_cars.vroom.modules.auth.service.AuthService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles authentication-related operations that are not part of the registration flow:
 * <ul>
 *   <li>Get me — returns the full internal profile enriched with minimal Keycloak data</li>
 *   <li>Forgot password — triggers a Keycloak password-reset email</li>
 *   <li>Change password — verifies the current password via Keycloak ROPC grant,
 *       then sets the new one via the Keycloak Admin API</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserProfileRepository userProfileRepository;
    private final CurrentUserService currentUserService;

    // ── Forgot-password throttle config ──────────────────────────────────────────

    @Value("${auth.password-reset.resend-interval-minutes:2}")
    private int passwordResetIntervalMinutes;

    @Value("${auth.password-reset.daily-max-retries:5}")
    private int passwordResetDailyMaxRetries;

    private final ConcurrentHashMap<String, AtomicInteger> dailyPasswordResetCounter = new ConcurrentHashMap<>();

    // ─── Get Me ───────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>Execution budget</h3>
     * <ul>
     *   <li>1 DB query — {@code findByKeycloakUserId}</li>
     *   <li>Up to 3 Keycloak Admin calls — {@code isEmailVerified}, {@code getFederatedIdentityProviders},
     *       {@code hasLocalPassword}</li>
     * </ul>
     *
     * <p>Each Keycloak call is individually fail-safe: on any exception the method logs the error
     * and falls back to a safe default, ensuring the endpoint always returns a response.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public UserMeResponse getMe() {
        // ── Step 1: resolve caller from JWT ──────────────────────────────────────
        String keycloakUserId = currentUserService.getCurrentKeycloakUserId();
        log.debug("GET /me for keycloakUserId={}", keycloakUserId);

        // ── Step 2: single DB query ───────────────────────────────────────────────
        UserProfile profile = userProfileRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new NotFoundException(
                        "No user profile found for keycloakUserId: " + keycloakUserId));

        // ── Step 3: minimal Keycloak enrichment (each call is independently fail-safe) ──
        boolean emailVerified  = keycloakAdminService.isEmailVerified(keycloakUserId);
        Set<String> providers  = keycloakAdminService.getFederatedIdentityProviders(keycloakUserId);
        boolean hasPassword    = keycloakAdminService.hasLocalPassword(keycloakUserId);

        log.debug("Keycloak enrichment for keycloakUserId={}: emailVerified={}, providers={}, hasLocalPassword={}",
                keycloakUserId, emailVerified, providers, hasPassword);

        // ── Step 4: assemble response ─────────────────────────────────────────────
        return UserMeResponse.builder()
                .id(profile.getId())
                .keycloakUserId(profile.getKeycloakUserId())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .phoneNumber(profile.getPhoneNumber())
                .avatarUrl(profile.getAvatarUrl())
                .status(profile.getStatus())
                .createdAt(profile.getCreatedAt())
                .emailVerified(emailVerified)
                .authProviders(providers)
                .hasLocalPassword(hasPassword)
                .build();
    }

    // ─── Forgot password ──────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>Security note</h3>
     * Always returns a generic 200 response to prevent user enumeration.
     * Rate-limit {@code BadRequestException}s ARE surfaced intentionally — they only
     * fire for a known email that has already exceeded its own daily cap,
     * which doesn't reveal new information to an attacker.
     *
     * <h3>Guard chain</h3>
     * <ol>
     *   <li>Normalize email</li>
     *   <li>Existence check — silent generic response if not found</li>
     *   <li>Account state — silent generic response for SUSPENDED / INACTIVE</li>
     *   <li>Minimum resend interval (DB timestamp)</li>
     *   <li>Daily cap (in-memory counter)</li>
     *   <li>Trigger Keycloak {@code UPDATE_PASSWORD} action email</li>
     * </ol>
     */
    @Override
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        final String genericMessage = "If an account exists, a password reset email has been sent.";
        String email = request.getEmail().toLowerCase().trim();

        log.info("Forgot-password requested for email={}", email);

        // ── Guard 1: existence (silent) ───────────────────────────────────────────
        UserProfile profile = userProfileRepository.findByEmail(email).orElse(null);
        if (profile == null) {
            log.info("Forgot-password: no profile found for email={}, returning generic response", email);
            return new ForgotPasswordResponse(genericMessage);
        }

        // ── Guard 2: account state (silent) ──────────────────────────────────────
        if (profile.getStatus() == UserStatus.SUSPENDED || profile.getStatus() == UserStatus.INACTIVE) {
            log.info("Forgot-password: account status={} for email={}, returning generic response",
                    profile.getStatus(), email);
            return new ForgotPasswordResponse(genericMessage);
        }

        // ── Guard 3: minimum interval ─────────────────────────────────────────────
        if (profile.getLastPasswordResetEmailSentAt() != null) {
            Instant earliest = profile.getLastPasswordResetEmailSentAt()
                    .plus(passwordResetIntervalMinutes, java.time.temporal.ChronoUnit.MINUTES);
            if (Instant.now().isBefore(earliest)) {
                long seconds = java.time.Duration.between(Instant.now(), earliest).getSeconds();
                throw new BadRequestException(
                        "Please wait " + seconds + " seconds before requesting another password reset email.");
            }
        }

        // ── Guard 4: daily cap ────────────────────────────────────────────────────
        String dailyKey = email + ":" + LocalDate.now();
        evictStaleDailyKeys(email);
        AtomicInteger todayCount = dailyPasswordResetCounter.computeIfAbsent(dailyKey, k -> new AtomicInteger(0));
        if (todayCount.get() >= passwordResetDailyMaxRetries) {
            throw new BadRequestException(
                    "Daily password reset email limit (" + passwordResetDailyMaxRetries
                            + ") reached. Please try again tomorrow.");
        }

        // ── Trigger Keycloak ──────────────────────────────────────────────────────
        keycloakAdminService.sendPasswordResetEmail(profile.getKeycloakUserId());
        log.info("Password-reset email triggered for email={} keycloakUserId={} (attempt {} today)",
                email, profile.getKeycloakUserId(), todayCount.get() + 1);

        todayCount.incrementAndGet();
        profile.setLastPasswordResetEmailSentAt(Instant.now());
        userProfileRepository.save(profile);

        return new ForgotPasswordResponse(genericMessage);
    }

    // ─── Change password ──────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>Flow</h3>
     * <ol>
     *   <li>Resolve caller's email from the JWT ({@link CurrentUserService})</li>
     *   <li>Load {@link UserProfile} — must exist and be {@code ACTIVE}</li>
     *   <li>Validate {@code newPassword == confirmNewPassword}</li>
     *   <li>Verify {@code currentPassword} via Keycloak ROPC grant —
     *       throws {@link BadRequestException} if wrong</li>
     *   <li>Set new password via Keycloak Admin API (permanent, non-temporary)</li>
     * </ol>
     */
    @Override
    @Transactional
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        // ── Step 1: resolve caller ────────────────────────────────────────────────
        String email = currentUserService.getCurrentEmail();
        UserProfile profile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("No user profile found for authenticated user."));

        log.info("Change-password requested for email={} keycloakUserId={}", email, profile.getKeycloakUserId());

        // ── Step 2: account must be ACTIVE ────────────────────────────────────────
        if (profile.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException(
                    "Password change is not allowed. Account status: " + profile.getStatus());
        }

        // ── Step 3: confirm passwords match ──────────────────────────────────────
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BadRequestException("New password and confirm password do not match.");
        }

        // ── Step 4: verify current password via Keycloak ROPC ────────────────────
        boolean credentialsValid = keycloakAdminService.verifyUserCredentials(email, request.getCurrentPassword());
        if (!credentialsValid) {
            log.warn("Change-password: invalid current password supplied for email={}", email);
            throw new BadRequestException("Current password is incorrect.");
        }

        // ── Step 5: set new password via Keycloak Admin API ──────────────────────
        keycloakAdminService.resetPassword(profile.getKeycloakUserId(), request.getNewPassword(), false);
        log.info("Password successfully changed for email={} keycloakUserId={}", email, profile.getKeycloakUserId());

        return new ChangePasswordResponse("Password changed successfully.");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Evicts stale (previous-day) entries from the forgot-password daily counter.
     * Prevents unbounded map growth in long-running instances.
     */
    private void evictStaleDailyKeys(String email) {
        String todayKey = email + ":" + LocalDate.now();
        dailyPasswordResetCounter.keySet()
                .removeIf(key -> key.startsWith(email + ":") && !key.equals(todayKey));
    }
}

