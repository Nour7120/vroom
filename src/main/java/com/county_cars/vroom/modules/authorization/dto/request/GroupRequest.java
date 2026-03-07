package com.county_cars.vroom.modules.authorization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Request to create or update a group")
public class GroupRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Unique group name", example = "MODERATORS")
    private String name;

    @Size(max = 512)
    @Schema(description = "Group description")
    private String description;

    @Schema(description = "Set of permission IDs to assign to this group")
    private Set<Long> permissionIds;
}

