package com.county_cars.vroom.modules.authorization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "UserGroup membership response")
public class UserGroupResponse {
    private Long id;
    private Long userProfileId;
    private String userEmail;
    private Long groupId;
    private String groupName;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
}

