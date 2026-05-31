package com.county_cars.vroom.modules.registration.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import com.county_cars.vroom.modules.keycloak.dto.CreateKeycloakUserRequest;
import com.county_cars.vroom.modules.registration.dto.CompleteRegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationResponse;
import com.county_cars.vroom.modules.registration.dto.ResendVerificationRequest;
import com.county_cars.vroom.modules.registration.service.RegistrationService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the full user registration flow.
 *
 * <h3>Normal registration steps</h3>
 * <ol>
 *   <li>Validate email / displayName uniqueness in the DB (with status-specific messages)</li>
 *   <li>Create user in Keycloak — Keycloak triggers its own VERIFY_EMAIL action</li>
 *   <li>Persist {@link UserProfile} with status {@code PENDING_MAIL_VERIFICATION}</li>
 *   <li>If DB save fails → delete the Keycloak user (compensating rollback)</li>
 * </ol>
 *
 * <h3>Third-party registration steps</h3>
 * <ol>
 *   <li>Verify the caller is authenticated (JWT present)</li>
 *   <li>Guard against double-completion (keycloakUserId already has a profile)</li>
 *   <li>Validate the supplied email matches the JWT email claim (if present)</li>
 *   <li>Validate email / displayName uniqueness with status-aware conflict messages</li>
 *   <li>Persist {@link UserProfile} with status {@code ACTIVE} immediately (email already verified by provider)</li>
 * </ol>
 *
 * <p>Resend throttling uses a DB timestamp (interval guard) and an in-process
 * {@link ConcurrentHashMap} (daily cap). Swap the map for Redis for multi-instance deployments.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserProfileRepository userProfileRepository;
    private final CurrentUserService currentUserService;

    @Value("${registration.verification.resend-interval-minutes:2}")
    private int resendIntervalMinutes;

    @Value("${registration.verification.daily-max-retries:5}")
    private int dailyMaxRetries;

    private final ConcurrentHashMap<String, AtomicInteger> dailyResendCounter = new ConcurrentHashMap<>();


    // ─── Normal Registration ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String displayName = request.getDisplayName().trim();

        // Edge case: email already taken — give status-specific messages.
        assertEmailAvailableForNewRegistration(email);

        if (userProfileRepository.existsByDisplayName(displayName)) {
            throw new ConflictException("Display name is already taken: " + displayName);
        }

        String keycloakUserId = keycloakAdminService.createUser(
                CreateKeycloakUserRequest.builder()
                        .email(email)
                        .displayName(displayName)
                        .firstName("Empty")
                        .lastName("Empty")
                        .password(request.getPassword())
                        .sendVerificationEmail(true)
                        .enabled(true)
                        .build()
        );

        UserProfile profile;
        try {
            profile = UserProfile.builder()
                    .keycloakUserId(keycloakUserId)
                    .email(email)
                    .displayName(displayName)
                    .phoneNumber(request.getPhoneNumber())
                    .status(UserStatus.PENDING_MAIL_VERIFICATION)
                    .lastVerificationEmailSentAt(Instant.now())
                    .build();
            profile = userProfileRepository.save(profile);
            log.info("UserProfile persisted: id={} email={}", profile.getId(), email);
        } catch (Exception dbEx) {
            log.error("DB save failed for email={}, rolling back Keycloak user id={}", email, keycloakUserId, dbEx);
            keycloakAdminService.deleteUser(keycloakUserId);
            throw new IllegalStateException("Registration failed during profile persistence. Please try again.", dbEx);
        }

        return RegistrationResponse.builder()
                .userProfileId(profile.getId())
                .keycloakUserId(keycloakUserId)
                .email(email)
                .displayName(profile.getDisplayName())
                .status(UserStatus.PENDING_MAIL_VERIFICATION)
                .message("Registration successful. A verification email has been sent to " + email
                        + ". Please verify your email to activate your account.")
                .build();
    }


    // ─── Third-Party Registration ─────────────────────────────────────────────────

    @Override
    @Transactional
    public RegistrationResponse completeThirdPartyRegistration(CompleteRegistrationRequest request) {
        String currentKeycloakUserId = currentUserService.getCurrentKeycloakUserId();

        // Edge case: already completed (e.g. double submit or retry after success).
        if (userProfileRepository.existsByKeycloakUserId(currentKeycloakUserId)) {
            throw new ConflictException(
                    "Registration already completed for this account. Use GET /api/v1/auth/me to view your profile.");
        }

        String email = request.getEmail().toLowerCase().trim();

        // Edge case: supplied email must match the email on the JWT (if the provider exposed one).
        // This prevents a third-party user from hijacking another user's email address.
        String jwtEmail = currentUserService.getCurrentEmail();
        if (jwtEmail != null && !jwtEmail.isBlank() && !jwtEmail.equalsIgnoreCase(email)) {
            throw new BadRequestException(
                    "The provided email does not match the email on your authenticated account. "
                    + "Please use the email address associated with your social login.");
        }

        String displayName = request.getDisplayName().trim();

        // Edge case: email taken — give status-specific messages.
        assertEmailAvailableForNewRegistration(email);

        if (userProfileRepository.existsByDisplayName(displayName)) {
            throw new ConflictException("Display name is already taken: " + displayName);
        }

        UserProfile profile;
        try {
            profile = UserProfile.builder()
                    .keycloakUserId(currentKeycloakUserId)
                    .email(email)
                    .displayName(displayName)
                    .phoneNumber(request.getPhoneNumber())
                    .status(UserStatus.ACTIVE)
                    .build();
            profile = userProfileRepository.save(profile);
            log.info("Third-party UserProfile persisted: id={} email={}", profile.getId(), email);
        } catch (Exception dbEx) {
            throw new IllegalStateException("Registration failed during profile persistence. Please try again.", dbEx);
        }

        return RegistrationResponse.builder()
                .userProfileId(profile.getId())
                .keycloakUserId(currentKeycloakUserId)
                .email(email)
                .displayName(profile.getDisplayName())
                .status(UserStatus.ACTIVE)
                .message("Registration successful.")
                .build();
    }


    // ─── Resend Verification Email ────────────────────────────────────────────────

    @Override
    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Fetch by email regardless of status so we can give specific, actionable error messages.
        UserProfile profile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("No account found for email: " + email));

        // Edge case: account already active — verification not needed.
        if (profile.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Account is already active. No email verification is required.");
        }

        // Edge case: account suspended or inactive — cannot resend.
        if (profile.getStatus() != UserStatus.PENDING_MAIL_VERIFICATION) {
            throw new BadRequestException(
                    "Email verification is not applicable for accounts with status: " + profile.getStatus());
        }

        // Enforce minimum resend interval.
        if (profile.getLastVerificationEmailSentAt() != null) {
            Instant earliest = profile.getLastVerificationEmailSentAt()
                    .plus(resendIntervalMinutes, java.time.temporal.ChronoUnit.MINUTES);
            if (Instant.now().isBefore(earliest)) {
                long seconds = java.time.Duration.between(Instant.now(), earliest).getSeconds();
                throw new BadRequestException(
                        "Please wait " + seconds + " seconds before requesting another verification email.");
            }
        }

        // Enforce daily cap (in-process counter; use Redis for multi-instance deployments).
        String dailyKey = email + ":" + LocalDate.now();
        evictStaleDailyKeys(email);
        AtomicInteger todayCount = dailyResendCounter.computeIfAbsent(dailyKey, k -> new AtomicInteger(0));
        if (todayCount.get() >= dailyMaxRetries) {
            throw new BadRequestException(
                    "Daily verification email limit (" + dailyMaxRetries + ") reached. Please try again tomorrow.");
        }

        keycloakAdminService.sendVerificationEmail(profile.getKeycloakUserId());
        log.info("Verification email re-triggered for email={} (attempt {} today)", email, todayCount.get() + 1);

        todayCount.incrementAndGet();
        profile.setLastVerificationEmailSentAt(Instant.now());
        userProfileRepository.save(profile);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Checks whether the given email is free to use for a brand-new registration.
     *
     * <ul>
     *   <li>ACTIVE / INACTIVE / SUSPENDED → "Email already registered"</li>
     *   <li>PENDING_MAIL_VERIFICATION → "Verification email already sent — check inbox or resend"</li>
     * </ul>
     *
     * @throws ConflictException if the email is already in use
     */
    private void assertEmailAvailableForNewRegistration(String email) {
        userProfileRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getStatus() == UserStatus.PENDING_MAIL_VERIFICATION) {
                throw new ConflictException(
                        "A verification email has already been sent to " + email
                        + ". Please check your inbox or use resend-verification to get a new link.");
            }
            throw new ConflictException("Email is already registered: " + email);
        });
    }

    private void evictStaleDailyKeys(String email) {
        String todayKey = email + ":" + LocalDate.now();
        dailyResendCounter.keySet().removeIf(key -> key.startsWith(email + ":") && !key.equals(todayKey));
    }
}
