package com.county_cars.vroom.modules.garage.dto.request;

import com.county_cars.vroom.modules.garage.entity.GarageVehicleCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to add a vehicle to the user's garage")
public class AddToGarageRequest {

    @NotNull
    @Schema(description = "ID of the vehicle to add", example = "1")
    private Long vehicleId;

    @NotNull
    @Schema(description = "Garage category", example = "OWNED")
    private GarageVehicleCategory category;

    @Schema(description = "Optional personal notes about this vehicle", example = "My daily driver")
    private String notes;
}

