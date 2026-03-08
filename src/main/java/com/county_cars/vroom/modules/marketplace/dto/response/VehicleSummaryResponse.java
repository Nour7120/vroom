package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Vehicle info embedded in listing details")
public class VehicleSummaryResponse {

    @Schema(description = "Vehicle ID", example = "3")
    private Long id;

    @Schema(description = "Vehicle make", example = "Toyota")
    private String make;

    @Schema(description = "Vehicle model", example = "Corolla")
    private String model;

    @Schema(description = "Year of manufacture", example = "2019")
    private Integer yearOfManufacture;

    @Schema(description = "Current mileage", example = "45000")
    private Long currentMileage;

    @Schema(description = "Fuel type", example = "PETROL")
    private String fuelType;

    @Schema(description = "Transmission", example = "MANUAL")
    private String transmission;

    @Schema(description = "Colour", example = "Midnight Black")
    private String colour;

    @Schema(description = "Body type", example = "Hatchback")
    private String bodyType;
}
