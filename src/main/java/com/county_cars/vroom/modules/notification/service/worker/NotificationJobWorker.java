package com.county_cars.vroom.modules.notification.service.worker;

import com.county_cars.vroom.modules.notification.config.NotificationProperties;
import com.county_cars.vroom.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Scheduled worker that drives notification fan-out.
 *
 * <h3>Tick behaviour</h3>
 * Each tick attempts up to {@code notification.worker-pool-size} job batches sequentially.
 * <ol>
 *   <li>Calls {@link NotificationService#processNextJobBatch()} — locks one job with
 *       {@code FOR UPDATE SKIP LOCKED}, creates notification rows for a cursor batch,
 *       advances the cursor, and commits the transaction.</li>
 *   <li>If notifications were created <em>and</em> push is enabled, calls
 *       {@link NotificationService#pushBatchAndRecord(List)} <b>after</b> the TX from
 *       step 1 has committed — satisfying the "DB first, push after commit" contract.</li>
 *   <li>Stops early if no job is available.</li>
 * </ol>
 *
 * <h3>Multi-instance safety</h3>
 * {@code FOR UPDATE SKIP LOCKED} ensures that two instances running simultaneously
 * never process the same job batch. Each grabs a different job (or skips if all
 * eligible jobs are already locked).
 *
 * <h3>Crash recovery</h3>
 * Jobs marked {@code IN_PROGRESS} with a non-zero cursor are re-eligible immediately
 * after a crash — the SKIP LOCKED query picks them up and resumes from the saved cursor.
 * Any user already processed is skipped by the {@code existsByJobIdAndUserId} idempotency check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJobWorker {

    private final NotificationProperties props;
    private final NotificationService    notificationService;

    @Scheduled(fixedDelayString = "${notification.job-processor-interval-ms:10000}")
    public void processJobs() {
        log.debug("[JobWorker] tick — attempting up to {} batch(es)", props.getWorkerPoolSize());
        int processed = 0;

        for (int i = 0; i < props.getWorkerPoolSize(); i++) {

            // ── Step 1: fan-out TX (commits before returning) ─────────────
            Optional<List<Long>> result = notificationService.processNextJobBatch();

            if (result.isEmpty()) {
                // No eligible job found — stop polling until next tick
                break;
            }

            processed++;
            List<Long> notificationIds = result.get();

            // ── Step 2: push AFTER TX-1 committed ────────────────────────
            if (!notificationIds.isEmpty() && props.isPushEnabled()) {
                log.debug("[JobWorker] Pushing {} notification(s) after commit", notificationIds.size());
                notificationService.pushBatchAndRecord(notificationIds);
            } else if (!props.isPushEnabled()) {
                log.debug("[JobWorker] Push disabled — {} notification(s) saved to DB only", notificationIds.size());
            }
        }

        if (processed > 0) {
            log.debug("[JobWorker] Completed {} batch(es) this tick", processed);
        }
    }
}

