package com.county_cars.vroom.modules.garage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update the display order of a vehicle media item")
public class UpdateMediaDisplayOrderRequest {

    @NotNull
    @Min(1)
    @Schema(description = "New 1-based display order. Setting this to 1 makes the item the thumbnail.",
            example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer displayOrder;
}

