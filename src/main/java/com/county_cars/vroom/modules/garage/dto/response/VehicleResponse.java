package com.county_cars.vroom.modules.garage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Vehicle identity and snapshot details")
public class VehicleResponse {

    @Schema(description = "Vehicle ID")
    private Long id;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Schema(description = "Registration number")
    private String registrationNumber;

    @Schema(description = "VIN")
    private String vin;

    // ── Classification ────────────────────────────────────────────────────────

    @Schema(description = "Make", example = "Toyota")
    private String make;

    @Schema(description = "Model", example = "Corolla")
    private String model;

    @Schema(description = "Variant")
    private String variant;

    @Schema(description = "Year of manufacture", example = "2019")
    private Integer yearOfManufacture;

    // ── Technical spec ────────────────────────────────────────────────────────

    @Schema(description = "Fuel type")
    private String fuelType;

    @Schema(description = "Transmission")
    private String transmission;

    @Schema(description = "Engine capacity in cc")
    private Integer engineCapacity;

    @Schema(description = "Colour")
    private String colour;

    @Schema(description = "Number of doors")
    private Integer numberOfDoors;

    @Schema(description = "Body type")
    private String bodyType;

    @Schema(description = "CO2 emissions in g/km")
    private Integer co2Emissions;

    // ── Current snapshot ──────────────────────────────────────────────────────

    @Schema(description = "Current mileage")
    private Long currentMileage;

    @Schema(description = "First registration date")
    private LocalDate firstRegistrationDate;

    @Schema(description = "Number of previous owners")
    private Integer previousOwners;

    @Schema(description = "MOT expiry date")
    private LocalDate motExpiryDate;

    @Schema(description = "Tax expiry date")
    private LocalDate taxExpiryDate;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Schema(description = "When this record was created")
    private LocalDateTime createdAt;

    @Schema(description = "When this record was last updated")
    private LocalDateTime updatedAt;
}

