package com.county_cars.vroom.modules.notification.service.worker;

import com.county_cars.vroom.modules.notification.config.NotificationProperties;
import com.county_cars.vroom.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled worker that retries failed FCM push notifications.
 *
 * <h3>Tick behaviour</h3>
 * <ol>
 *   <li>Skips immediately if {@code notification.retry-enabled=false}.</li>
 *   <li>Calls {@link NotificationService#claimRetryBatch()} — resets eligible FAILED
 *       notifications to PENDING in a committed TX.</li>
 *   <li>Calls {@link NotificationService#pushBatchAndRecord(List)} <b>after</b> the
 *       claim TX commits — satisfying "DB first, push after commit".</li>
 * </ol>
 *
 * <h3>Eligibility criteria</h3>
 * A notification is eligible for retry when:
 * <ul>
 *   <li>{@code push_status = FAILED}</li>
 *   <li>{@code attempt_count < notification.max-attempts}</li>
 *   <li>{@code next_retry_at <= NOW()} (backoff window has elapsed)</li>
 * </ul>
 *
 * <h3>Backoff schedule</h3>
 * Set by {@code notification.backoff-delays-ms} (default: 60s, 5m, 15m).
 * After the final allowed attempt, the notification stays FAILED permanently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryWorker {

    private final NotificationProperties props;
    private final NotificationService    notificationService;

    @Scheduled(fixedDelayString = "${notification.retry-processor-interval-ms:30000}")
    public void retryFailed() {
        if (!props.isRetryEnabled()) {
            log.debug("[RetryWorker] Retry disabled — skipping tick");
            return;
        }

        log.debug("[RetryWorker] tick — scanning for eligible retries (maxAttempts={})", props.getMaxAttempts());

        // ── Step 1: claim eligible notifications (TX commits before returning) ──
        List<Long> ids = notificationService.claimRetryBatch();

        if (ids.isEmpty()) {
            log.debug("[RetryWorker] Nothing to retry");
            return;
        }

        // ── Step 2: push AFTER claim TX committed ─────────────────────────────
        if (props.isPushEnabled()) {
            log.info("[RetryWorker] Retrying {} notification(s)", ids.size());
            notificationService.pushBatchAndRecord(ids);
        } else {
            log.debug("[RetryWorker] Push disabled — {} notification(s) reset to PENDING only", ids.size());
        }
    }
}

