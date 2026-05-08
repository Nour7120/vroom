package com.county_cars.vroom.modules.notification.service;

import java.util.Map;

/**
 * Abstracts Firebase Cloud Messaging delivery.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link com.county_cars.vroom.modules.notification.service.impl.FcmServiceImpl}
 *       — real FCM via Firebase Admin SDK.</li>
 *   <li>Mock mode — activated by {@code notification.fcm.mock-enabled=true};
 *       no real push is sent, a synthetic success is returned and logged.</li>
 * </ul>
 * </p>
 */
public interface FcmService {

    /**
     * Send a push notification to a single device token.
     *
     * @param token    FCM registration token for the target device.
     * @param title    Notification title.
     * @param body     Notification body text.
     * @param imageUrl Optional image URL (may be null).
     * @param data     Optional key-value data payload (may be null or empty).
     * @return {@link FcmResult} — never null; check {@code result.success()} for outcome.
     */
    FcmResult send(String token, String title, String body, String imageUrl, Map<String, String> data);
}
