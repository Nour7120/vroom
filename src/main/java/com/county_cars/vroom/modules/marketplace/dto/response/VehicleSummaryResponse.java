package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Vehicle info embedded in listing details – sourced directly from Vehicle Passport")
public class VehicleSummaryResponse {

    @Schema(description = "Vehicle ID", example = "3")
    private Long id;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Schema(description = "Registration number", example = "191-D-12345")
    private String registrationNumber;

    @Schema(description = "VIN / chassis number", example = "WBA3A5G59DNP26082")
    private String vin;

    // ── Classification ────────────────────────────────────────────────────────

    @Schema(description = "Vehicle make", example = "Toyota")
    private String make;

    @Schema(description = "Vehicle model", example = "Corolla")
    private String model;

    @Schema(description = "Variant / trim level", example = "GR Sport")
    private String variant;

    @Schema(description = "Year of manufacture", example = "2019")
    private Integer yearOfManufacture;

    // ── Technical spec ────────────────────────────────────────────────────────

    @Schema(description = "Fuel type", example = "PETROL")
    private String fuelType;

    @Schema(description = "Transmission", example = "MANUAL")
    private String transmission;

    @Schema(description = "Engine capacity in cc", example = "1998")
    private Integer engineCapacity;

    @Schema(description = "Exterior colour", example = "Midnight Black")
    private String colour;

    @Schema(description = "Number of doors", example = "5")
    private Integer numberOfDoors;

    @Schema(description = "Body type", example = "Hatchback")
    private String bodyType;

    @Schema(description = "CO₂ emissions in g/km", example = "115")
    private Integer co2Emissions;

    // ── Current snapshot ──────────────────────────────────────────────────────

    @Schema(description = "Current odometer reading in miles", example = "45000")
    private Long currentMileage;

    @Schema(description = "Date of first registration", example = "2019-06-15")
    private LocalDate firstRegistrationDate;

    @Schema(description = "Number of previous registered owners", example = "1")
    private Integer previousOwners;

    @Schema(description = "MOT expiry date – null if unknown", example = "2025-06-14")
    private LocalDate motExpiryDate;

    @Schema(description = "Road tax / VED expiry date – null if unknown", example = "2025-01-31")
    private LocalDate taxExpiryDate;
}
