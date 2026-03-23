package com.county_cars.vroom.modules.garage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "One entry in the vehicle ownership timeline")
public class VehicleOwnershipResponse {

    @Schema(description = "Ownership record ID")
    private Long id;

    @Schema(description = "Owner's user profile ID")
    private Long ownerId;

    @Schema(description = "Owner display name")
    private String ownerDisplayName;

    @Schema(description = "Ownership start date")
    private LocalDate ownershipStart;

    @Schema(description = "Ownership end date — null means current owner")
    private LocalDate ownershipEnd;

    @Schema(description = "Whether this is the current owner")
    private Boolean isCurrent;
}

