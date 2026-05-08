package com.county_cars.vroom.modules.notification.repository;

import com.county_cars.vroom.modules.notification.entity.NotificationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    /**
     * Atomically selects the next eligible job using {@code FOR UPDATE SKIP LOCKED}.
     *
     * <p>This is safe for multiple concurrent workers (same or different instances):
     * each worker grabs a different job, and locked rows are skipped rather than
     * blocked, preventing pile-ups under load.</p>
     *
     * <p>Eligible statuses: PENDING (not yet started), IN_PROGRESS (resumed after crash).</p>
     */
    @Query(value = """
            SELECT * FROM notification_jobs
            WHERE status IN ('PENDING', 'IN_PROGRESS')
              AND is_deleted = false
              AND (scheduled_at IS NULL OR scheduled_at <= NOW())
            ORDER BY id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationJob> findAndLockNextJob();

    Page<NotificationJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

