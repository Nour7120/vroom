package com.county_cars.vroom.modules.notification.service;

/**
 * Immutable result of a single FCM push attempt to one device token.
 *
 * @param success      {@code true} if FCM accepted the message.
 * @param fcmMessageId The message ID returned by FCM on success (e.g. {@code projects/my-app/messages/0:1234}).
 * @param errorCode    FCM MessagingErrorCode name on failure (e.g. {@code UNREGISTERED}).
 * @param errorMessage Human-readable error text on failure.
 */
public record FcmResult(
        boolean success,
        String fcmMessageId,
        String errorCode,
        String errorMessage
) {
    public static FcmResult success(String fcmMessageId) {
        return new FcmResult(true, fcmMessageId, null, null);
    }

    public static FcmResult failure(String errorCode, String errorMessage) {
        return new FcmResult(false, null, errorCode, errorMessage);
    }
}
