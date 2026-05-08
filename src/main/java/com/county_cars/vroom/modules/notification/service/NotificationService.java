package com.county_cars.vroom.modules.notification.service;

import com.county_cars.vroom.modules.notification.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface NotificationService {

    // Admin

    NotificationJobResponse createJob(CreateNotificationJobRequest request);

    Page<NotificationJobResponse> listJobs(Pageable pageable);

    NotificationJobResponse getJob(Long jobId);

    NotificationJobResponse cancelJob(Long jobId);

    // User

    Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable);

    void markAsRead(Long userId, Long notificationId);

    void markAllAsRead(Long userId);

    long countUnread(Long userId);

    void delete(Long userId, Long notificationId);

    // Devices

    UserDeviceResponse registerDevice(Long userId, RegisterDeviceRequest request);

    void unregisterDevice(Long userId, Long deviceId);

    List<UserDeviceResponse> listMyDevices(Long userId);

    // Internal (worker use only)

    /**
     * Claims the next eligible job with {@code FOR UPDATE SKIP LOCKED} and processes
     * one batch of users using cursor-based pagination.
     *
     * @return {@code Optional.empty()} if no job was available.
     *         {@code Optional.of(ids)} where {@code ids} is the list of
     *         {@code notification.id} values created in this batch (may be empty when
     *         the job just completed its last batch).
     */
    Optional<List<Long>> processNextJobBatch();

    /**
     * Sends FCM push notifications for the given notification IDs and records the result.
     *
     * <p>Must be called <em>after</em> the fan-out transaction has committed so that
     * the notification rows are visible to all workers. Uses a programmatic
     * {@code TransactionTemplate} internally to allow per-notification TX boundaries.</p>
     *
     * @param notificationIds IDs of {@code Notification} rows with {@code push_status = PENDING}.
     */
    void pushBatchAndRecord(List<Long> notificationIds);

    /**
     * Finds FAILED notifications eligible for retry (past their backoff window,
     * below max attempts), resets them to PENDING, and returns their IDs so the
     * caller can invoke {@link #pushBatchAndRecord} after this TX commits.
     *
     * @return list of notification IDs reset to PENDING (empty if nothing is eligible).
     */
    List<Long> claimRetryBatch();
}

