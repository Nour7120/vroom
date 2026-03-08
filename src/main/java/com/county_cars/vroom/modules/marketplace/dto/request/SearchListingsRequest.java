package com.county_cars.vroom.modules.marketplace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Filter parameters for marketplace search")
public class SearchListingsRequest {

    @Schema(description = "Vehicle make filter", example = "Toyota")
    private String make;

    @Schema(description = "Vehicle model filter", example = "Corolla")
    private String model;

    @Schema(description = "Minimum year of manufacture", example = "2015")
    private Integer yearMin;

    @Schema(description = "Maximum year of manufacture", example = "2022")
    private Integer yearMax;

    @Schema(description = "Minimum asking price", example = "5000.00")
    private BigDecimal priceMin;

    @Schema(description = "Maximum asking price", example = "25000.00")
    private BigDecimal priceMax;

    @Schema(description = "Minimum mileage", example = "0")
    private Long mileageMin;

    @Schema(description = "Maximum mileage", example = "100000")
    private Long mileageMax;

    @Schema(description = "Fuel type filter", example = "PETROL")
    private String fuelType;

    @Schema(description = "Transmission filter", example = "MANUAL")
    private String transmission;

    @Schema(description = "Colour filter", example = "Black")
    private String colour;

    @Schema(description = "Location filter (partial match)", example = "Dublin")
    private String location;

    // ── Cross-field validation ─────────────────────────────────────────────────

    @AssertTrue(message = "yearMin must be less than or equal to yearMax")
    @Schema(hidden = true)
    public boolean isYearRangeValid() {
        return yearMin == null || yearMax == null || yearMin <= yearMax;
    }

    @AssertTrue(message = "priceMin must be less than or equal to priceMax")
    @Schema(hidden = true)
    public boolean isPriceRangeValid() {
        return priceMin == null || priceMax == null || priceMin.compareTo(priceMax) <= 0;
    }

    @AssertTrue(message = "mileageMin must be less than or equal to mileageMax")
    @Schema(hidden = true)
    public boolean isMileageRangeValid() {
        return mileageMin == null || mileageMax == null || mileageMin <= mileageMax;
    }
}

