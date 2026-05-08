package com.county_cars.vroom.modules.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Schema(description = "Request body to create a new broadcast notification job")
public class CreateNotificationJobRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 255, message = "title must not exceed 255 characters")
    @Schema(description = "Push notification title", example = "New feature available!")
    private String title;

    @NotBlank(message = "body must not be blank")
    @Schema(description = "Push notification body text", example = "Check out the new car valuation tool in your garage.")
    private String body;

    @Size(max = 1024, message = "imageUrl must not exceed 1024 characters")
    @Schema(description = "Optional public image URL shown in the notification", example = "https://cdn.vroom.com/promos/banner.jpg")
    private String imageUrl;

    @Schema(description = "Optional key-value map forwarded as the FCM data payload (string values only)",
            example = "{\"screen\": \"GARAGE\", \"promo_id\": \"SUMMER2026\"}")
    private Map<String, String> data;

    @Schema(description = "Optional: earliest time the job worker may start processing. Null = start immediately.",
            example = "2026-05-01T09:00:00Z")
    private Instant scheduledAt;
}

