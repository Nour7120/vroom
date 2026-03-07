package com.county_cars.vroom.modules.authorization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to create a permission")
public class PermissionRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Unique permission name", example = "USER_READ")
    private String name;

    @Size(max = 512)
    @Schema(description = "Description")
    private String description;
}

