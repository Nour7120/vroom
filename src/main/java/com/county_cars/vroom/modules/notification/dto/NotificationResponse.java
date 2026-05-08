package com.county_cars.vroom.modules.notification.dto;

import com.county_cars.vroom.modules.notification.entity.NotificationPushStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "A single notification displayed in the user's notification center")
public class NotificationResponse {

    @Schema(description = "Notification ID")
    private Long id;

    @Schema(description = "ID of the job that created this notification")
    private Long jobId;

    @Schema(description = "Notification title")
    private String title;

    @Schema(description = "Notification body text")
    private String body;

    @Schema(description = "Optional image URL")
    private String imageUrl;

    @Schema(description = "Optional FCM data payload (key-value map)")
    private Map<String, String> data;

    @Schema(description = "Whether the user has read this notification")
    private boolean read;

    @Schema(description = "Timestamp when the notification was read (null if unread)")
    private Instant readAt;

    @Schema(description = "FCM push delivery status")
    private NotificationPushStatus pushStatus;

    @Schema(description = "Number of FCM push attempts made")
    private Integer attemptCount;

    @Schema(description = "Notification creation timestamp")
    private LocalDateTime createdAt;
}

