package com.county_cars.vroom.modules.notification.controller;

import com.county_cars.vroom.modules.notification.dto.*;
import com.county_cars.vroom.modules.notification.service.NotificationService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Push notification center and broadcast job management")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService  currentUserService;

    @GetMapping("/api/v1/notifications")
    @Operation(summary = "List my notifications (newest first, pageable)")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Long userId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.ok(notificationService.getMyNotifications(userId, pageable));
    }

    @GetMapping("/api/v1/notifications/unread-count")
    @Operation(summary = "Get unread notification count (badge counter)")
    public ResponseEntity<UnreadCountResponse> countUnread() {
        Long userId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.countUnread(userId)));
    }

    @PutMapping("/api/v1/notifications/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserProfileId();
        notificationService.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/notifications/read-all")
    @Operation(summary = "Mark all unread notifications as read")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = currentUserService.getCurrentUserProfileId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/notifications/{id}")
    @Operation(summary = "Delete a single notification (soft-delete)")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserProfileId();
        notificationService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }


    // Devices Related APIs.


    @PostMapping("/api/v1/devices")
    @Operation(summary = "Register or refresh an FCM device token",
               description = "Call this on every app launch or whenever the FCM SDK rotates the token. " +
                             "If the token already exists it is reassigned to the current user and reactivated.")
    public ResponseEntity<UserDeviceResponse> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request) {
        Long userId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.registerDevice(userId, request));
    }

    @DeleteMapping("/api/v1/devices/{deviceId}")
    @Operation(summary = "Unregister a device token (soft-delete)")
    public ResponseEntity<Void> unregisterDevice(@PathVariable Long deviceId) {
        Long userId = currentUserService.getCurrentUserProfileId();
        notificationService.unregisterDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/devices")
    @Operation(summary = "List all registered devices for the current user")
    public ResponseEntity<List<UserDeviceResponse>> listMyDevices() {
        Long userId = currentUserService.getCurrentUserProfileId();
        return ResponseEntity.ok(notificationService.listMyDevices(userId));
    }


    // Admin Related APIs.


    @PostMapping("/api/v1/admin/notification-jobs")
    @Operation(summary = "[ADMIN] Create and queue a broadcast notification job",
               description = "Creates a PENDING job that the worker will fan-out to all active users. " +
                             "Use scheduledAt to defer the start time.")
    public ResponseEntity<NotificationJobResponse> createJob(
            @Valid @RequestBody CreateNotificationJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createJob(request));
    }

    @GetMapping("/api/v1/admin/notification-jobs")
    @Operation(summary = "[ADMIN] List all notification jobs (newest first, pageable)")
    public ResponseEntity<Page<NotificationJobResponse>> listJobs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.listJobs(pageable));
    }

    @GetMapping("/api/v1/admin/notification-jobs/{jobId}")
    @Operation(summary = "[ADMIN] Get notification job details and fan-out progress")
    public ResponseEntity<NotificationJobResponse> getJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(notificationService.getJob(jobId));
    }

    @PutMapping("/api/v1/admin/notification-jobs/{jobId}/cancel")
    @Operation(summary = "[ADMIN] Cancel a PENDING or IN_PROGRESS notification job",
               description = "Sets status to CANCELLED. Does not undo notifications already sent.")
    public ResponseEntity<NotificationJobResponse> cancelJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(notificationService.cancelJob(jobId));
    }
}

