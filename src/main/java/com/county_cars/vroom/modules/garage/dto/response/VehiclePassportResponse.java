package com.county_cars.vroom.modules.garage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Aggregated Vehicle Passport — full history, documents, media and ownership timeline")
public class VehiclePassportResponse {

    // ── Vehicle identity ──────────────────────────────────────────────────────

    @Schema(description = "Vehicle ID")
    private Long vehicleId;

    @Schema(description = "Registration number")
    private String registrationNumber;

    @Schema(description = "VIN")
    private String vin;

    @Schema(description = "Make")
    private String make;

    @Schema(description = "Model")
    private String model;

    @Schema(description = "Variant")
    private String variant;

    @Schema(description = "Year of manufacture")
    private Integer yearOfManufacture;

    @Schema(description = "Fuel type")
    private String fuelType;

    @Schema(description = "Transmission")
    private String transmission;

    @Schema(description = "Engine capacity (cc)")
    private Integer engineCapacity;

    @Schema(description = "Colour")
    private String colour;

    @Schema(description = "Number of doors")
    private Integer numberOfDoors;

    @Schema(description = "Body type")
    private String bodyType;

    @Schema(description = "CO2 emissions (g/km)")
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

    // ── Aggregated history ────────────────────────────────────────────────────

    @Schema(description = "Mileage history ordered by recorded date descending")
    private List<MileageHistoryEntry> mileageHistory;

    @Schema(description = "MOT history ordered by test date descending")
    private List<MotHistoryEntry> motHistory;

    @Schema(description = "Valuation history ordered by valuation date descending")
    private List<ValuationHistoryEntry> valuationHistory;

    @Schema(description = "Ownership timeline ordered by start date descending")
    private List<VehicleOwnershipResponse> ownershipTimeline;

    @Schema(description = "Documents attached to this vehicle")
    private List<VehicleDocumentResponse> documents;

    @Schema(description = "Media (photos/videos) attached to this vehicle")
    private List<VehicleMediaResponse> media;

    // ── Nested history entry types ────────────────────────────────────────────

    @Data
    public static class MileageHistoryEntry {
        private Long id;
        private Long mileage;
        private LocalDate recordedDate;
        private String source;
    }

    @Data
    public static class MotHistoryEntry {
        private Long id;
        private LocalDate testDate;
        private LocalDate expiryDate;
        private String result;
        private String advisory;
        private String failureItems;
        private Long mileage;
    }

    @Data
    public static class ValuationHistoryEntry {
        private Long id;
        private BigDecimal dealerRetailValue;
        private BigDecimal tradeInValue;
        private BigDecimal privateSaleValue;
        private BigDecimal auctionValue;
        private BigDecimal averageMarketValue;
        private String valuationConfidence;
        private LocalDateTime valuationDate;
    }
}

