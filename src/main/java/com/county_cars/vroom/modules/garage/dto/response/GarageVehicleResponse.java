package com.county_cars.vroom.modules.garage.dto.response;

import com.county_cars.vroom.modules.garage.entity.GarageVehicleCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "A vehicle entry in the user's digital garage")
public class GarageVehicleResponse {

    @Schema(description = "Garage entry ID")
    private Long id;

    @Schema(description = "Garage category")
    private GarageVehicleCategory category;

    @Schema(description = "Personal notes")
    private String notes;

    @Schema(description = "When this vehicle was added to the garage")
    private LocalDateTime createdAt;

    // ── Vehicle snapshot ──────────────────────────────────────────────────────

    @Schema(description = "Vehicle ID")
    private Long vehicleId;

    @Schema(description = "Registration number")
    private String registrationNumber;

    @Schema(description = "Make", example = "Toyota")
    private String make;

    @Schema(description = "Model", example = "Corolla")
    private String model;

    @Schema(description = "Variant")
    private String variant;

    @Schema(description = "Year of manufacture")
    private Integer yearOfManufacture;

    @Schema(description = "Fuel type")
    private String fuelType;

    @Schema(description = "Transmission")
    private String transmission;

    @Schema(description = "Colour")
    private String colour;

    @Schema(description = "Current mileage")
    private Long currentMileage;

    @Schema(description = "MOT expiry date")
    private LocalDate motExpiryDate;

    @Schema(description = "Tax expiry date")
    private LocalDate taxExpiryDate;
}

