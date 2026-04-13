package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Most recent vehicle valuation snapshot embedded in listing details.
 * Helps buyers understand fair-market pricing context without requiring
 * a separate API call to the Vehicle Passport endpoints.
 */
@Data
@Schema(description = "Most recent vehicle valuation summary embedded in listing details")
public class ValuationSummaryResponse {

    @Schema(description = "Private sale estimate", example = "12000.00")
    private BigDecimal privateSaleValue;

    @Schema(description = "Average market value across all sale channels", example = "13500.00")
    private BigDecimal averageMarketValue;

    @Schema(description = "Dealer retail estimate (highest channel)", example = "15000.00")
    private BigDecimal dealerRetailValue;

    @Schema(description = "Trade-in / part-exchange estimate", example = "10500.00")
    private BigDecimal tradeInValue;

    @Schema(description = "Confidence level of the valuation: HIGH, MEDIUM or LOW", example = "HIGH")
    private String valuationConfidence;

    @Schema(description = "Timestamp when this valuation was recorded")
    private LocalDateTime valuationDate;
}

