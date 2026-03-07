package com.county_cars.vroom.modules.keycloak.webhooks;

import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakEventWebhookService {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void handleEvent(KeycloakEventPayload payload) {
        if ("VERIFY_EMAIL".equals(payload.getType())) {
            handleEmailVerified(payload.getUserId());
        }// You can handle other events here: LOGIN, LOGOUT, UPDATE_PASSWORD, etc.
    }

    private void handleEmailVerified(String keycloakUserId) {
        UserProfile profile = userProfileRepository
                .findByKeycloakUserId(keycloakUserId)
                .orElse(null);

        if (profile == null) {
            log.warn("VERIFY_EMAIL event received for unknown keycloakUserId={}", keycloakUserId);
            return;
        }

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