package com.county_cars.vroom.modules.notification.entity;

public enum NotificationPushStatus {
    /** Created in the DB; FCM push has not been attempted yet. */
    PENDING,
    /** At least one device received the FCM push successfully. */
    SENT,
    /** All FCM push attempts failed; eligible for retry. */
    FAILED,
    /** User has no active device tokens — push was intentionally skipped. */
    SKIPPED
}

