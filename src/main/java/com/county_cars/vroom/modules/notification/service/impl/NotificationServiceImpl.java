package com.county_cars.vroom.modules.notification.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.notification.config.NotificationProperties;
import com.county_cars.vroom.modules.notification.dto.*;
import com.county_cars.vroom.modules.notification.entity.*;
import com.county_cars.vroom.modules.notification.repository.*;
import com.county_cars.vroom.modules.notification.service.FcmResult;
import com.county_cars.vroom.modules.notification.service.FcmService;
import com.county_cars.vroom.modules.notification.service.NotificationService;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core implementation of the notification fan-out and delivery pipeline.
 *
 * <h3>Fan-out flow (per worker tick)</h3>
 * <ol>
 *   <li><b>TX-1 (commit)</b>: {@link #processNextJobBatch()} — claim job with SKIP LOCKED,
 *       create PENDING notification rows for the current cursor batch, advance cursor.</li>
 *   <li><b>After TX-1 commits</b>: caller invokes {@link #pushBatchAndRecord(List)} —
 *       loads tokens, calls FCM, records results in individual TX-2 per notification.</li>
 * </ol>
 *
 * <h3>Retry flow (per retry tick)</h3>
 * <ol>
 *   <li><b>TX-1 (commit)</b>: {@link #claimRetryBatch()} — find FAILED + eligible + below max attempts,
 *       reset to PENDING, return IDs.</li>
 *   <li><b>After TX-1 commits</b>: caller invokes {@link #pushBatchAndRecord(List)} — same as above.</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * The DB unique constraint {@code uq_notification_job_user (job_id, user_id)} is the hard guarantee.
 * An {@code existsByJobIdAndUserId} check provides a fast path to skip the insert.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationJobRepository     jobRepo;
    private final NotificationRepository        notificationRepo;
    private final UserDeviceRepository          deviceRepo;
    private final NotificationAttemptRepository attemptRepo;
    private final UserProfileRepository         userProfileRepo;
    private final FcmService                    fcmService;
    private final NotificationProperties        props;
    private final TransactionTemplate           transactionTemplate;

    // ADMIN — Job management

    @Override
    @Transactional
    public NotificationJobResponse createJob(CreateNotificationJobRequest request) {
        NotificationJob job = NotificationJob.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .imageUrl(request.getImageUrl())
                .data(request.getData())
                .status(NotificationJobStatus.PENDING)
                .targetType(NotificationJobTargetType.ALL)
                .cursorId(0L)
                .processedUsers(0)
                .totalUsers(0)
                .scheduledAt(request.getScheduledAt())
                .build();

        job = jobRepo.save(job);
        log.info("[NotifJob] Created — id={} title='{}' scheduledAt={}", job.getId(), job.getTitle(), job.getScheduledAt());
        return toJobResponse(job);
    }

    @Override
    public Page<NotificationJobResponse> listJobs(Pageable pageable) {
        return jobRepo.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toJobResponse);
    }

    @Override
    public NotificationJobResponse getJob(Long jobId) {
        return toJobResponse(requireJob(jobId));
    }

    @Override
    @Transactional
    public NotificationJobResponse cancelJob(Long jobId) {
        NotificationJob job = requireJob(jobId);
        if (job.getStatus() == NotificationJobStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed job");
        }
        if (job.getStatus() == NotificationJobStatus.CANCELLED) {
            throw new BadRequestException("Job is already cancelled");
        }
        job.setStatus(NotificationJobStatus.CANCELLED);
        log.info("[NotifJob] Cancelled — id={}", jobId);
        return toJobResponse(jobRepo.save(job));
    }

    // USER — Notification center

    @Override
    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepo
                .findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toNotificationResponse);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification n = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));

        if (!n.getUserId().equals(userId)) {
            throw new UnauthorizedException("Notification does not belong to this user");
        }
        if (n.isRead()) return;

        n.setRead(true);
        n.setReadAt(Instant.now());
        notificationRepo.save(n);
        log.debug("[Notification] Marked read — notificationId={} userId={}", notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepo.markAllReadByUserId(userId, Instant.now());
        log.debug("[Notification] markAllAsRead — userId={} updated={}", userId, updated);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationRepo.countByUserIdAndReadFalse(userId);
    }

    @Override
    public void delete(Long userId, Long notificationId) {
        Notification n = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found or is already deleted: " + notificationId));

        if (!n.getUserId().equals(userId)) {
            throw new UnauthorizedException("Notification does not belong to this user");
        }

        n.setIsDeleted(true);
        n.setDeletedAt(LocalDateTime.now());
        notificationRepo.save(n);
        log.debug("[Notification] Deleted — notificationId={} userId={}", notificationId, userId);
    }

    // Devices

    @Override
    @Transactional
    public UserDeviceResponse registerDevice(Long userId, RegisterDeviceRequest request) {
        // Upsert: if token already exists (even for another user), reassign + reactivate.
        Optional<UserDevice> existing = deviceRepo.findByToken(request.getToken());

        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.setUserId(userId);
            device.setPlatform(request.getPlatform());
            device.setActive(true);
            device.setLastSeenAt(Instant.now());
            log.debug("[Device] Token upserted — userId={} deviceId={} platform={}", userId, device.getId(), device.getPlatform());
            return toDeviceResponse(deviceRepo.save(device));
        }

        UserDevice device = UserDevice.builder()
                .userId(userId)
                .token(request.getToken())
                .platform(request.getPlatform())
                .active(true)
                .lastSeenAt(Instant.now())
                .build();

        device = deviceRepo.save(device);
        log.info("[Device] Registered — userId={} deviceId={} platform={}", userId, device.getId(), device.getPlatform());
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public void unregisterDevice(Long userId, Long deviceId) {
        UserDevice device = deviceRepo.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));

        device.setActive(false);
        device.setIsDeleted(true);
        device.setDeletedAt(LocalDateTime.now());
        deviceRepo.save(device);
        log.info("[Device] Unregistered — userId={} deviceId={}", userId, deviceId);
    }

    @Override
    public List<UserDeviceResponse> listMyDevices(Long userId) {
        return deviceRepo.findByUserId(userId)
                .stream()
                .map(this::toDeviceResponse)
                .toList();
    }

    // INTERNAL — Fan-out batch (called by NotificationJobWorker)

    /**
     * Transaction boundary: this entire method runs in one DB transaction.
     * The caller (worker) invokes it first; after it returns (and the TX commits),
     * the caller calls {@link #pushBatchAndRecord} with the returned IDs.
     */
    @Override
    @Transactional
    public Optional<List<Long>> processNextJobBatch() {
        // 1. Claim a job (SKIP LOCKED — safe for concurrent workers / instances)
        Optional<NotificationJob> optJob = jobRepo.findAndLockNextJob();
        if (optJob.isEmpty()) {
            log.debug("[FanOut] No eligible jobs found");
            return Optional.empty();
        }

        NotificationJob job = optJob.get();

        // 2. Transition PENDING → IN_PROGRESS
        if (job.getStatus() == NotificationJobStatus.PENDING) {
            job.setStatus(NotificationJobStatus.IN_PROGRESS);
            job.setStartedAt(Instant.now());
        }

        // 3. Cursor-based user batch
        List<Long> userIds = userProfileRepo.findActiveIdsAfterCursor(
                job.getCursorId(),
                PageRequest.of(0, props.getBatchSize()));

        log.info("[FanOut] Job id={} cursor={} batchSize={}",
                job.getId(), job.getCursorId(), userIds.size());

        // 4. No more users → complete the job
        if (userIds.isEmpty()) {
            job.setStatus(NotificationJobStatus.COMPLETED);
            job.setTotalUsers(job.getProcessedUsers());
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
            log.info("[FanOut] Job id={} COMPLETED — total_processed={}",
                    job.getId(), job.getProcessedUsers());
            return Optional.of(List.of());
        }

        // 5. Create PENDING notification rows (idempotent)
        List<Notification> toSave = new ArrayList<>(userIds.size());
        int skipped = 0;
        for (Long uid : userIds) {
            if (notificationRepo.existsByJobIdAndUserId(job.getId(), uid)) {
                skipped++;
                continue; // already created in a previous run (idempotent)
            }
            toSave.add(Notification.builder()
                    .jobId(job.getId())
                    .userId(uid)
                    .title(job.getTitle())
                    .body(job.getBody())
                    .imageUrl(job.getImageUrl())
                    .data(job.getData())
                    .pushStatus(NotificationPushStatus.PENDING)
                    .attemptCount(0)
                    .build());
        }

        List<Notification> saved = notificationRepo.saveAll(toSave);

        // 6. Advance cursor to last processed user ID
        Long newCursor = userIds.getLast();
        job.setCursorId(newCursor);
        job.setProcessedUsers(job.getProcessedUsers() + userIds.size());
        jobRepo.save(job);

        log.info("[FanOut] Job id={} batch committed — created={} skipped={} cursor={} totalProcessed={}",
                job.getId(), saved.size(), skipped, newCursor, job.getProcessedUsers());

        return Optional.of(saved.stream().map(Notification::getId).toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL — Push delivery (called AFTER fan-out TX commits)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Pushes FCM notifications for the given notification IDs.
     *
     * <p>NOT annotated with {@code @Transactional} at the outer level.
     * Each notification update uses a programmatic {@link TransactionTemplate}
     * to allow partial success: if one notification fails to update, others
     * are not rolled back. The FCM call itself is outside any TX.</p>
     */
    @Override
    public void pushBatchAndRecord(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) return;

        // Load all PENDING notifications in a single read TX
        List<Notification> notifications = transactionTemplate.execute(
                status -> notificationRepo.findPendingByIds(notificationIds, NotificationPushStatus.PENDING));

        if (notifications == null || notifications.isEmpty()) {
            log.debug("[Push] No PENDING notifications to push for ids={}", notificationIds);
            return;
        }

        log.info("[Push] Pushing {} notification(s)", notifications.size());

        for (Notification n : notifications) {
            pushSingleNotification(n);
        }
    }

    /**
     * Pushes to all active devices for one notification.
     * Records the attempt and updates notification status in its own TX.
     */
    private void pushSingleNotification(Notification n) {
        List<UserDevice> devices = deviceRepo.findByUserIdAndActiveTrue(n.getUserId());

        if (devices.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                Notification fresh = notificationRepo.findById(n.getId()).orElse(null);
                if (fresh == null) return;
                fresh.setPushStatus(NotificationPushStatus.SKIPPED);
                fresh.setAttemptCount(fresh.getAttemptCount() + 1);
                fresh.setLastAttemptAt(Instant.now());
                notificationRepo.save(fresh);
            });
            log.debug("[Push] notification={} SKIPPED — userId={} has no active devices",
                    n.getId(), n.getUserId());
            return;
        }

        int attemptNumber = n.getAttemptCount() + 1;
        boolean anySuccess = false;
        List<NotificationAttempt> attemptsToSave = new ArrayList<>(devices.size());

        for (UserDevice device : devices) {
            // FCM call — intentionally OUTSIDE any DB transaction
            FcmResult result = fcmService.send(
                    device.getToken(), n.getTitle(), n.getBody(), n.getImageUrl(), n.getData());

            attemptsToSave.add(NotificationAttempt.builder()
                    .notificationId(n.getId())
                    .deviceId(device.getId())
                    .attemptNumber(attemptNumber)
                    .status(result.success()
                            ? NotificationAttemptStatus.SUCCESS
                            : NotificationAttemptStatus.FAILED)
                    .fcmMessageId(result.fcmMessageId())
                    .errorCode(result.errorCode())
                    .errorMessage(result.errorMessage())
                    .build());

            if (result.success()) {
                anySuccess = true;
                log.debug("[Push] notification={} device={} SUCCESS — msgId={}",
                        n.getId(), device.getId(), result.fcmMessageId());
            } else {
                log.warn("[Push] notification={} device={} FAILED — code={} msg={}",
                        n.getId(), device.getId(), result.errorCode(), result.errorMessage());
            }
        }

        // Persist attempt records and update notification status in one TX
        final boolean finalAnySuccess = anySuccess;
        final List<NotificationAttempt> finalAttempts = attemptsToSave;

        transactionTemplate.executeWithoutResult(status -> {
            attemptRepo.saveAll(finalAttempts);

            Notification fresh = notificationRepo.findById(n.getId()).orElse(null);
            if (fresh == null) return;

            fresh.setAttemptCount(fresh.getAttemptCount() + 1);
            fresh.setLastAttemptAt(Instant.now());

            if (finalAnySuccess) {
                fresh.setPushStatus(NotificationPushStatus.SENT);
                fresh.setNextRetryAt(null);
            } else {
                fresh.setPushStatus(NotificationPushStatus.FAILED);
                // Pass current (already-incremented) attempt count to backoff calculator
                fresh.setNextRetryAt(computeNextRetryAt(fresh.getAttemptCount() + 1));
            }
            notificationRepo.save(fresh);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL — Retry claim (called by NotificationRetryWorker)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds FAILED notifications eligible for retry, resets them to PENDING, and
     * returns their IDs. The reset and the subsequent push happen in separate TXs
     * (DB first, push after commit).
     */
    @Override
    @Transactional
    public List<Long> claimRetryBatch() {
        Instant now = Instant.now();
        List<Notification> eligible = notificationRepo.findEligibleForRetry(
                NotificationPushStatus.FAILED,
                props.getMaxAttempts(),
                now,
                PageRequest.of(0, props.getBatchSize()));

        if (eligible.isEmpty()) {
            log.debug("[Retry] No eligible notifications");
            return List.of();
        }

        eligible.forEach(n -> {
            n.setPushStatus(NotificationPushStatus.PENDING);
            n.setNextRetryAt(null);
        });
        notificationRepo.saveAll(eligible);

        List<Long> ids = eligible.stream().map(Notification::getId).toList();
        log.info("[Retry] Claimed {} notification(s) for retry — ids={}", ids.size(), ids);
        return ids;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Computes the next retry timestamp using the configured backoff schedule. */
    private Instant computeNextRetryAt(int failureCount) {
        List<Long> delays = props.getBackoffDelaysMs();
        if (delays.isEmpty()) return Instant.now().plusSeconds(60);
        int idx = Math.min(failureCount - 1, delays.size() - 1);
        return Instant.now().plusMillis(delays.get(idx));
    }

    private NotificationJob requireJob(Long jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Notification job not found: " + jobId));
    }

    // Mappers

    private NotificationJobResponse toJobResponse(NotificationJob job) {
        return NotificationJobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .body(job.getBody())
                .imageUrl(job.getImageUrl())
                .data(job.getData())
                .status(job.getStatus())
                .targetType(job.getTargetType())
                .totalUsers(job.getTotalUsers())
                .processedUsers(job.getProcessedUsers())
                .cursorId(job.getCursorId())
                .scheduledAt(job.getScheduledAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private NotificationResponse toNotificationResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .jobId(n.getJobId())
                .title(n.getTitle())
                .body(n.getBody())
                .imageUrl(n.getImageUrl())
                .data(n.getData())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .pushStatus(n.getPushStatus())
                .attemptCount(n.getAttemptCount())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private UserDeviceResponse toDeviceResponse(UserDevice d) {
        return UserDeviceResponse.builder()
                .id(d.getId())
                .platform(d.getPlatform())
                .active(d.isActive())
                .lastSeenAt(d.getLastSeenAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}






