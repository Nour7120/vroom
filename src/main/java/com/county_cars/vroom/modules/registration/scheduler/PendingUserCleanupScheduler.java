package com.county_cars.vroom.modules.registration.scheduler;

import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class PendingUserCleanupScheduler {

    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${registration.cleanup.expiry-minutes}")
    private int expiryMinutes;

    @Value("${registration.cleanup.batch-size}")
    private int batchSize;

    @Scheduled(fixedRateString = "${registration.cleanup.interval-ms}")
    @Transactional
    public void cleanupExpiredPendingUsers() {
        Instant cutoff = Instant.now().minus(expiryMinutes, ChronoUnit.MINUTES);
        log.info("Cleanup scheduler started — purging PENDING_MAIL_VERIFICATION users with lastVerificationEmailSentAt < {}", cutoff);

        List<UserProfile> expired = userProfileRepository.findByStatusAndLastVerificationEmailSentAtBefore(
                UserStatus.PENDING_MAIL_VERIFICATION,
                cutoff,
                PageRequest.of(0, batchSize)
        );

        if (expired.isEmpty()) {
            log.info("Cleanup scheduler finished — no expired PENDING users found");
            return;
        }

        log.info("Cleanup scheduler found {} expired PENDING user(s) to remove", expired.size());

        int deleted = 0;
        int failed  = 0;

        for (UserProfile profile : expired) {
            try {
                keycloakAdminService.deleteUser(profile.getKeycloakUserId());

                profile.setIsDeleted(true);
                profile.setDeletedAt(LocalDateTime.now());
                profile.setDeletedBy("system-cleanup-scheduler");
                userProfileRepository.save(profile);

                log.info("Cleanup: removed expired PENDING user email={} keycloakUserId={}",
                        profile.getEmail(), profile.getKeycloakUserId());
                deleted++;
            } catch (Exception e) {
                log.error("Cleanup: failed to remove expired PENDING user email={} keycloakUserId={}: {}",
                        profile.getEmail(), profile.getKeycloakUserId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Cleanup scheduler finished — deleted={}, failed={}", deleted, failed);
    }
}