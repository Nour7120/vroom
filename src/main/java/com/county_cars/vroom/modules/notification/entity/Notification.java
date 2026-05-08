package com.county_cars.vroom.modules.notification.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Per-user notification center entry.
 *
 * <p>The unique constraint {@code (job_id, user_id)} enforces idempotency:
 * even if the fan-out worker runs twice for the same user, only one row is created.</p>
 *
 * <p>Push lifecycle:  PENDING → SENT (success) | FAILED (all devices failed) | SKIPPED (no devices).</p>
 * <p>Failed notifications with {@code attempt_count < maxAttempts} and {@code next_retry_at <= now}
 * are picked up by the retry worker on the next scheduled tick.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> data;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 32)
    @Builder.Default
    private NotificationPushStatus pushStatus = NotificationPushStatus.PENDING;

    /** Total number of FCM push attempts made (across all retry cycles). */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** Timestamp of the most recent push attempt (initial or retry). */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
     * Earliest time the retry worker may attempt a push for this notification.
     * Set after a failure using the configured backoff schedule.
     * {@code null} means "never retried" or "no retry needed".
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
}


