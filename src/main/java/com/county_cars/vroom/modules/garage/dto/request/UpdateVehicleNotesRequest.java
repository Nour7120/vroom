package com.county_cars.vroom.modules.garage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to update personal notes for a garage vehicle")
public class UpdateVehicleNotesRequest {

    @NotNull
    @Schema(description = "Vehicle ID", example = "1")
    private Long vehicleId;

    @Size(max = 1000)
    @Schema(description = "Personal notes", example = "Needs new tyres in spring")
    private String notes;
}

