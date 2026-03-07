package com.county_cars.vroom.modules.registration.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import com.county_cars.vroom.modules.keycloak.dto.CreateKeycloakUserRequest;
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
 * <h3>Steps</h3>
 * <ol>
 *   <li>Validate email / displayName uniqueness in the DB</li>
 *   <li>Create user in Keycloak — Keycloak triggers its own VERIFY_EMAIL action</li>
 *   <li>Persist {@link UserProfile} with status {@code PENDING_MAIL_VERIFICATION}</li>
 *   <li>If DB save fails → delete the Keycloak user (compensating rollback)</li>
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

    @Value("${registration.verification.resend-interval-minutes:2}")
    private int resendIntervalMinutes;

    @Value("${registration.verification.daily-max-retries:5}")
    private int dailyMaxRetries;

    private final ConcurrentHashMap<String, AtomicInteger> dailyResendCounter = new ConcurrentHashMap<>();

    // ─── Register ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String displayName = request.getDisplayName().trim();

        if (userProfileRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered: " + email);
        }
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

    // ─── Resend verification email ────────────────────────────────────────────────

    @Override
    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        UserProfile profile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("No account found for email: " + email));

        if (profile.getStatus() != UserStatus.PENDING_MAIL_VERIFICATION) {
            throw new BadRequestException(
                    "Account is not pending verification. Current status: " + profile.getStatus());
        }

        if (profile.getLastVerificationEmailSentAt() != null) {
            Instant earliest = profile.getLastVerificationEmailSentAt()
                    .plus(resendIntervalMinutes, java.time.temporal.ChronoUnit.MINUTES);
            if (Instant.now().isBefore(earliest)) {
                long seconds = java.time.Duration.between(Instant.now(), earliest).getSeconds();
                throw new BadRequestException(
                        "Please wait " + seconds + " seconds before requesting another verification email.");
            }
        }

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

    private void evictStaleDailyKeys(String email) {
        String todayKey = email + ":" + LocalDate.now();
        dailyResendCounter.keySet().removeIf(key -> key.startsWith(email + ":") && !key.equals(todayKey));
    }
}
