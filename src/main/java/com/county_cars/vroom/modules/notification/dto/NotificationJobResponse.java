package com.county_cars.vroom.modules.notification.dto;

import com.county_cars.vroom.modules.notification.entity.NotificationJobStatus;
import com.county_cars.vroom.modules.notification.entity.NotificationJobTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "Notification job details including fan-out progress")
public class NotificationJobResponse {

    @Schema(description = "Unique job ID")
    private Long id;

    @Schema(description = "Notification title")
    private String title;

    @Schema(description = "Notification body text")
    private String body;

    @Schema(description = "Optional image URL")
    private String imageUrl;

    @Schema(description = "Optional FCM data payload")
    private Map<String, String> data;

    @Schema(description = "Current job status")
    private NotificationJobStatus status;

    @Schema(description = "Fan-out target type")
    private NotificationJobTargetType targetType;

    @Schema(description = "Total users in scope (set on completion)")
    private Integer totalUsers;

    @Schema(description = "Number of users whose notification row has been created so far")
    private Integer processedUsers;

    @Schema(description = "Current cursor — last user_profile.id processed")
    private Long cursorId;

    @Schema(description = "Scheduled start time (null = immediately)")
    private Instant scheduledAt;

    @Schema(description = "When the first worker picked up this job")
    private Instant startedAt;

    @Schema(description = "When the job was fully completed")
    private Instant completedAt;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Record last-updated timestamp")
    private LocalDateTime updatedAt;
}

