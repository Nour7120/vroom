package com.county_cars.vroom.modules.garage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Request to update vehicle details")
public class UpdateVehicleRequest {

    @Size(max = 100)
    @Schema(description = "Make", example = "Toyota")
    private String make;

    @Size(max = 100)
    @Schema(description = "Model", example = "Corolla")
    private String model;

    @Size(max = 100)
    @Schema(description = "Variant / trim level", example = "Executive")
    private String variant;

    @Schema(description = "Year of manufacture", example = "2019")
    private Integer yearOfManufacture;

    @Size(max = 50)
    @Schema(description = "Fuel type", example = "PETROL")
    private String fuelType;

    @Size(max = 50)
    @Schema(description = "Transmission", example = "MANUAL")
    private String transmission;

    @Schema(description = "Engine capacity in cc", example = "1998")
    private Integer engineCapacity;

    @Size(max = 50)
    @Schema(description = "Colour", example = "Midnight Black")
    private String colour;

    @Schema(description = "Number of doors", example = "5")
    private Integer numberOfDoors;

    @Size(max = 50)
    @Schema(description = "Body type", example = "Hatchback")
    private String bodyType;

    @Schema(description = "CO2 emissions in g/km", example = "118")
    private Integer co2Emissions;

    @Schema(description = "Current mileage", example = "47500")
    private Long currentMileage;

    @Schema(description = "First registration date")
    private LocalDate firstRegistrationDate;

    @Schema(description = "Number of previous owners", example = "1")
    private Integer previousOwners;

    @Schema(description = "MOT expiry date")
    private LocalDate motExpiryDate;

    @Schema(description = "Tax expiry date")
    private LocalDate taxExpiryDate;
}

