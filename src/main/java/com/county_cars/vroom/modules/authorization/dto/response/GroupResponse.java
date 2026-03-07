package com.county_cars.vroom.modules.authorization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Schema(description = "Group response")
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private Set<PermissionResponse> permissions;
    private LocalDateTime createdAt;
}

