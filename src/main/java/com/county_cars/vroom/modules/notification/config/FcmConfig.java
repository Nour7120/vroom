package com.county_cars.vroom.modules.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Initializes the Firebase Admin SDK and exposes a {@link FirebaseMessaging} bean.
 *
 * <p>The bean is only created when {@code notification.fcm.mock-enabled=false} (default).
 * When mock mode is active, no Firebase SDK is initialized and
 * {@code FcmServiceImpl} detects the absence of the bean via {@code @Autowired(required = false)}.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FcmConfig {

    private static final String FIREBASE_MESSAGING_SCOPE =
            "https://www.googleapis.com/auth/firebase.messaging";

    private final NotificationProperties props;

    @Bean
    @ConditionalOnProperty(name = "notification.fcm.mock-enabled", havingValue = "false", matchIfMissing = true)
    public FirebaseMessaging firebaseMessaging() throws IOException {
        String credentialsPath = props.getFcm().getCredentialsPath();

        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("[FCM] notification.fcm.credentials-path is not configured — " +
                     "FCM push will be skipped (bean created but non-functional). " +
                     "Set notification.fcm.mock-enabled=true for local dev.");
            // Return null-safe stub — FcmServiceImpl guards against null FirebaseMessaging
            return null;
        }

        log.info("[FCM] Initializing Firebase Admin SDK from credentials: {}", credentialsPath);

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped(List.of(FIREBASE_MESSAGING_SCOPE));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setConnectTimeout(props.getFcm().getConnectTimeoutMs())
                .setReadTimeout(props.getFcm().getReadTimeoutMs())
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            log.info("[FCM] FirebaseApp initialized successfully");
        } else {
            log.debug("[FCM] FirebaseApp already initialized — reusing existing instance");
        }

        return FirebaseMessaging.getInstance();
    }
}


