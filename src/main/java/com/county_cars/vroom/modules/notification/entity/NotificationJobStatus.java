package com.county_cars.vroom.modules.notification.entity;

public enum NotificationJobStatus {
    /** Job created and waiting to be picked up by a worker. */
    PENDING,
    /** Worker has locked the job and is processing batches. */
    IN_PROGRESS,
    /** All users have been processed — fan-out complete. */
    COMPLETED,
    /** Job could not be processed due to an unrecoverable error. */
    FAILED,
    /** Manually cancelled before completion. */
    CANCELLED
}

