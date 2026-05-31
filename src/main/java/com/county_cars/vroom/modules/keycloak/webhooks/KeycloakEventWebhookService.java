package com.county_cars.vroom.modules.keycloak.webhooks;

import com.county_cars.vroom.modules.keycloak.KeycloakAdminService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakEventWebhookService {

    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Transactional
    public void handleEvent(KeycloakEventPayload payload) {
        if ("VERIFY_EMAIL".equals(payload.getType())) {
            handleEmailVerified(payload.getUserId());
        }
    }

    private void handleEmailVerified(String keycloakUserId) {
        UserProfile profile = userProfileRepository
                .findByKeycloakUserId(keycloakUserId)
                .orElse(null);

        if (profile == null) {
            log.warn("VERIFY_EMAIL event received for unknown keycloakUserId={}", keycloakUserId);
            return;
        }

        userProfileRepository.findByEmailAndStatusIn(profile.getEmail(), Set.of(UserStatus.ACTIVE))
                .ifPresent(existing -> {
                    if (existing.getKeycloakUserId().equals(keycloakUserId)) {
                        log.info("VERIFY_EMAIL event received but profile already active for keycloakUserId={} email={}",
                                keycloakUserId, profile.getEmail());
                    } else {
                        log.warn("VERIFY_EMAIL event received but email {} already associated with another active profile (keycloakUserId={})",
                                profile.getEmail(), existing.getKeycloakUserId());
                        keycloakAdminService.deleteUser(keycloakUserId);
                    }
                });

        if (profile.getStatus() == UserStatus.PENDING_MAIL_VERIFICATION) {
            profile.setStatus(UserStatus.ACTIVE);
            userProfileRepository.save(profile);
            log.info("UserProfile activated for keycloakUserId={} email={}", keycloakUserId, profile.getEmail());
        } else {
            log.info("VERIFY_EMAIL event received but profile already in status={} for keycloakUserId={}",
                    profile.getStatus(), keycloakUserId);
        }
    }
}