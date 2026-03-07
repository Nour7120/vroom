package com.county_cars.vroom.modules.authorization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Permission response")
public class PermissionResponse {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}

