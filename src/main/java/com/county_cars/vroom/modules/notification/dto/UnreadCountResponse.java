package com.county_cars.vroom.modules.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Unread notification count for the badge counter")
public class UnreadCountResponse {

    @Schema(description = "Number of unread notifications for the current user")
    private long count;
}

