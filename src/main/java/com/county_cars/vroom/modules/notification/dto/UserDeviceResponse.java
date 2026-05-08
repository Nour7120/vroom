package com.county_cars.vroom.modules.notification.dto;

import com.county_cars.vroom.modules.notification.entity.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Registered device token details")
public class UserDeviceResponse {

    @Schema(description = "Device record ID")
    private Long id;

    @Schema(description = "Device platform")
    private DevicePlatform platform;

    @Schema(description = "Whether this device token is currently active")
    private boolean active;

    @Schema(description = "Last time this device checked in")
    private Instant lastSeenAt;

    @Schema(description = "When the record was created")
    private LocalDateTime createdAt;
}

