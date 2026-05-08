package com.county_cars.vroom.modules.notification.service.impl;

import com.county_cars.vroom.modules.notification.config.NotificationProperties;
import com.county_cars.vroom.modules.notification.service.FcmResult;
import com.county_cars.vroom.modules.notification.service.FcmService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FCM delivery implementation.
 *
 * <h3>Mock mode ({@code notification.fcm.mock-enabled=true})</h3>
 * No Firebase SDK is initialized. Every call returns a synthetic success result
 * and logs the payload. Safe for local development and CI without real credentials.
 *
 * <h3>Real mode ({@code notification.fcm.mock-enabled=false}, default)</h3>
 * Uses the Firebase Admin SDK (initialized by
 * {@link com.county_cars.vroom.modules.notification.config.FcmConfig}).
 * When {@code firebaseMessaging} is {@code null} (credentials path not configured),
 * the method returns a failure result instead of throwing.
 */
@Slf4j
@Service
public class FcmServiceImpl implements FcmService {

    private final NotificationProperties props;

    /**
     * Injected only when {@code notification.fcm.mock-enabled=false} and credentials
     * are configured. {@code required = false} prevents a startup failure when mock
     * mode is active.
     */
    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    public FcmServiceImpl(NotificationProperties props) {
        this.props = props;
    }

    @Override
    public FcmResult send(String token, String title, String body, String imageUrl, Map<String, String> data) {

        // ── Mock mode ──────────────────────────────────────────────────────
        if (props.getFcm().isMockEnabled()) {
            String mockId = "mock-" + System.currentTimeMillis();
            log.info("[FCM MOCK] Push sent — token={}... title='{}' mockMessageId={}",
                    abbreviate(token), title, mockId);
            return FcmResult.success(mockId);
        }

        // ── Guard: SDK not initialized ────────────────────────────────────
        if (firebaseMessaging == null) {
            log.warn("[FCM] FirebaseMessaging bean is null — credentials not configured. " +
                     "Set notification.fcm.credentials-path or enable mock mode.");
            return FcmResult.failure("SDK_NOT_INITIALIZED",
                    "Firebase Admin SDK is not initialized. Check notification.fcm.credentials-path.");
        }

        // ── Real FCM delivery ─────────────────────────────────────────────
        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setImage(imageUrl)
                            .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String messageId = firebaseMessaging.send(builder.build());
            log.debug("[FCM] Delivered — token={}... messageId={}", abbreviate(token), messageId);
            return FcmResult.success(messageId);

        } catch (FirebaseMessagingException ex) {
            String errorCode = ex.getMessagingErrorCode() != null
                    ? ex.getMessagingErrorCode().name()
                    : "UNKNOWN";
            log.warn("[FCM] Delivery failed — token={}... errorCode={} message={}",
                    abbreviate(token), errorCode, ex.getMessage());
            return FcmResult.failure(errorCode, ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Returns first 12 chars of a token — safe to log (not sensitive in isolation). */
    private static String abbreviate(String token) {
        if (token == null) return "null";
        return token.length() > 12 ? token.substring(0, 12) + "..." : token;
    }
}
