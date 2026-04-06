package com.county_cars.vroom.modules.garage.dto.request;

import com.county_cars.vroom.modules.garage.entity.GarageVehicleCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create a new vehicle and add it to the garage in a single atomic request")
public class AddVehicleWithDetailsRequest {

    @Valid
    @NotNull
    @Schema(description = "Vehicle identity and spec details", requiredMode = Schema.RequiredMode.REQUIRED)
    private CreateVehicleRequest vehicle;

    @NotNull
    @Schema(description = "Garage category", example = "OWNED", requiredMode = Schema.RequiredMode.REQUIRED)
    private GarageVehicleCategory garageCategory;

    @Schema(description = "Optional personal notes about this vehicle", example = "My daily driver")
    private String notes;
}

