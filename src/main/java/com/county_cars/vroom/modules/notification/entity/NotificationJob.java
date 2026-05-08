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
 * Fan-out controller — one record per broadcast notification campaign.
 *
 * <p>A single job fans out to all active users in batches.
 * The {@link #cursorId} advances after each committed batch, allowing
 * workers to resume safely after a crash or restart.</p>
 */
@Entity
@Table(name = "notification_jobs")
@Getter
@Setter
@SQLRestriction("is_deleted = false")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    /** Optional FCM data payload. Stored as JSONB; mapped to Map at runtime. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private NotificationJobStatus status = NotificationJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    @Builder.Default
    private NotificationJobTargetType targetType = NotificationJobTargetType.ALL;

    /** Total number of users in scope (set when job completes). */
    @Column(name = "total_users", nullable = false)
    @Builder.Default
    private Integer totalUsers = 0;

    /** Count of users whose notification row has been created (advances per batch). */
    @Column(name = "processed_users", nullable = false)
    @Builder.Default
    private Integer processedUsers = 0;

    /**
     * Cursor: the highest {@code user_profile.id} processed in the last committed batch.
     * Next batch fetches {@code id > cursorId}. Starts at 0 (before any user).
     */
    @Column(name = "cursor_id", nullable = false)
    @Builder.Default
    private Long cursorId = 0L;

    /** Optional: if set, the worker will not start processing before this time. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Timestamp when the first worker picked up this job. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Timestamp when the last batch was processed and status set to COMPLETED. */
    @Column(name = "completed_at")
    private Instant completedAt;
}

