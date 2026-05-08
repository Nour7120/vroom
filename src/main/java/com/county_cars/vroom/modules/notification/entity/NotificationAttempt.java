package com.county_cars.vroom.modules.notification.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

/**
 * Records every FCM push attempt for a notification + device pair.
 *
 * <p>Used for auditing and debugging. One row per (notification, device, attempt_number).
 * The retry system reads {@link Notification#getAttemptCount()} and
 * {@link Notification#getNextRetryAt()} directly — not this table — to decide eligibility.</p>
 */
@Entity
@Table(name = "notification_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class NotificationAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to the notifications row this attempt belongs to. */
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    /** FK to the specific device that was targeted (nullable if no device exists). */
    @Column(name = "device_id")
    private Long deviceId;

    /** 1-based sequence counter: 1 = initial, 2 = first retry, … */
    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private NotificationAttemptStatus status = NotificationAttemptStatus.FAILED;

    /** FCM message ID returned on success (e.g. {@code projects/my-app/messages/123}). */
    @Column(name = "fcm_message_id", length = 512)
    private String fcmMessageId;

    /** FCM error code on failure (e.g. UNREGISTERED, INVALID_ARGUMENT). */
    @Column(name = "error_code", length = 255)
    private String errorCode;

    /** Full error message or exception message on failure. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

