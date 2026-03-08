package com.county_cars.vroom.modules.garage.dto.request;

import com.county_cars.vroom.modules.garage.entity.GarageVehicleCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to update garage category for a vehicle")
public class UpdateGarageCategoryRequest {

    @NotNull
    @Schema(description = "Vehicle ID", example = "1")
    private Long vehicleId;

    @NotNull
    @Schema(description = "New category", example = "WISHLIST")
    private GarageVehicleCategory category;
}

