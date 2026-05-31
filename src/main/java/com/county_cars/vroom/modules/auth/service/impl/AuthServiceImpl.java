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


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserProfileRepository userProfileRepository;
    private final CurrentUserService currentUserService;


    @Value("${auth.password-reset.resend-interval-minutes:2}")
    private int passwordResetIntervalMinutes;

    @Value("${auth.password-reset.daily-max-retries:5}")
    private int passwordResetDailyMaxRetries;

    private final ConcurrentHashMap<String, AtomicInteger> dailyPasswordResetCounter = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public UserMeResponse getMe() {

        String keycloakUserId = currentUserService.getCurrentKeycloakUserId();
        log.debug("GET /me for keycloakUserId={}", keycloakUserId);

        UserProfile profile = userProfileRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new NotFoundException(
                        "No user profile found for keycloakUserId: " + keycloakUserId));

        boolean emailVerified  = keycloakAdminService.isEmailVerified(keycloakUserId);
        Set<String> providers  = keycloakAdminService.getFederatedIdentityProviders(keycloakUserId);
        boolean hasPassword    = keycloakAdminService.hasLocalPassword(keycloakUserId);

        log.debug("Keycloak enrichment for keycloakUserId={}: emailVerified={}, providers={}, hasLocalPassword={}",
                keycloakUserId, emailVerified, providers, hasPassword);

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


    @Override
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        final String genericMessage = "If an account exists, a password reset email has been sent.";
        String email = request.getEmail().toLowerCase().trim();

        log.info("Forgot-password requested for email={}", email);

        UserProfile profile = userProfileRepository.findByEmailAndStatusIn(email, Set.of(UserStatus.ACTIVE)).orElse(null);
        if (profile == null) {
            log.info("Forgot-password: no profile found for email={}, returning generic response", email);
            return new ForgotPasswordResponse(genericMessage);
        }

        if (profile.getLastPasswordResetEmailSentAt() != null) {
            Instant earliest = profile.getLastPasswordResetEmailSentAt()
                    .plus(passwordResetIntervalMinutes, java.time.temporal.ChronoUnit.MINUTES);
            if (Instant.now().isBefore(earliest)) {
                long seconds = java.time.Duration.between(Instant.now(), earliest).getSeconds();
                throw new BadRequestException(
                        "Please wait " + seconds + " seconds before requesting another password reset email.");
            }
        }

        String dailyKey = email + ":" + LocalDate.now();
        evictStaleDailyKeys(email);
        AtomicInteger todayCount = dailyPasswordResetCounter.computeIfAbsent(dailyKey, k -> new AtomicInteger(0));
        if (todayCount.get() >= passwordResetDailyMaxRetries) {
            throw new BadRequestException(
                    "Daily password reset email limit (" + passwordResetDailyMaxRetries
                            + ") reached. Please try again tomorrow.");
        }

        keycloakAdminService.sendPasswordResetEmail(profile.getKeycloakUserId());
        log.info("Password-reset email triggered for email={} keycloakUserId={} (attempt {} today)",
                email, profile.getKeycloakUserId(), todayCount.get() + 1);

        todayCount.incrementAndGet();
        profile.setLastPasswordResetEmailSentAt(Instant.now());
        userProfileRepository.save(profile);

        return new ForgotPasswordResponse(genericMessage);
    }


    @Override
    @Transactional
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        String email = currentUserService.getCurrentEmail();
        UserProfile profile = userProfileRepository.findByEmailAndStatusIn(email, Set.of(UserStatus.ACTIVE))
                .orElseThrow(() -> new UnauthorizedException("No user profile found for authenticated user."));

        log.info("Change-password requested for email={} keycloakUserId={}", email, profile.getKeycloakUserId());

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BadRequestException("New password and confirm password do not match.");
        }

        boolean credentialsValid = keycloakAdminService.verifyUserCredentials(email, request.getCurrentPassword());
        if (!credentialsValid) {
            log.warn("Change-password: invalid current password supplied for email={}", email);
            throw new BadRequestException("Current password is incorrect.");
        }

        keycloakAdminService.resetPassword(profile.getKeycloakUserId(), request.getNewPassword(), false);
        log.info("Password successfully changed for email={} keycloakUserId={}", email, profile.getKeycloakUserId());

        return new ChangePasswordResponse("Password changed successfully.");
    }


    private void evictStaleDailyKeys(String email) {
        String todayKey = email + ":" + LocalDate.now();
        dailyPasswordResetCounter.keySet()
                .removeIf(key -> key.startsWith(email + ":") && !key.equals(todayKey));
    }
}

