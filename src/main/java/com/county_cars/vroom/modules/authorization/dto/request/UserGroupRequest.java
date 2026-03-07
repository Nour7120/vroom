package com.county_cars.vroom.modules.authorization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to assign a user to a group")
public class UserGroupRequest {

    @NotNull
    @Schema(description = "User profile ID")
    private Long userProfileId;

    @NotNull
    @Schema(description = "Group ID")
    private Long groupId;
}

