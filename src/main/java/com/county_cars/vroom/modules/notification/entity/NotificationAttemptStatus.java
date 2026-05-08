package com.county_cars.vroom.modules.notification.entity;

public enum NotificationAttemptStatus {
    /** FCM accepted the message and returned a message ID. */
    SUCCESS,
    /** FCM rejected the message or network error occurred. */
    FAILED
}

