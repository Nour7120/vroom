package com.county_cars.vroom.modules.notification.repository;

import com.county_cars.vroom.modules.notification.entity.Notification;
import com.county_cars.vroom.modules.notification.entity.NotificationPushStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Idempotency guard: check before inserting a notification for a (job, user) pair.
     * The DB unique constraint {@code uq_notification_job_user} is the hard guarantee;
     * this check is a fast-path to skip the insert entirely.
     */
    boolean existsByJobIdAndUserId(Long jobId, Long userId);

    /** Count unread notifications for the badge counter. */
    long countByUserIdAndReadFalse(Long userId);

    /**
     * Bulk-mark all unread notifications as read for a user.
     * More efficient than loading and iterating entities one by one.
     */
    @Modifying
    @Query("""
            UPDATE Notification n
               SET n.read    = true,
                   n.readAt  = :readAt
             WHERE n.userId     = :userId
               AND n.read       = false
               AND n.isDeleted  = false
            """)
    int markAllReadByUserId(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    /**
     * Loads PENDING notifications by ID for the push sender.
     * Called immediately after a fan-out batch transaction has committed.
     */
    @Query("SELECT n FROM Notification n WHERE n.id IN :ids AND n.pushStatus = :status")
    List<Notification> findPendingByIds(
            @Param("ids") List<Long> ids,
            @Param("status") NotificationPushStatus status);

    /**
     * Retry worker eligibility: FAILED, below max attempts, and past the backoff window.
     */
    @Query("""
            SELECT n FROM Notification n
             WHERE n.pushStatus   = :failedStatus
               AND n.attemptCount < :maxAttempts
               AND n.nextRetryAt <= :now
               AND n.isDeleted    = false
             ORDER BY n.id ASC
            """)
    List<Notification> findEligibleForRetry(
            @Param("failedStatus") NotificationPushStatus failedStatus,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now,
            Pageable pageable);

    /** Convenience: find by job and push status (admin diagnostics). */
    List<Notification> findByJobIdAndPushStatus(Long jobId, NotificationPushStatus status);
}


