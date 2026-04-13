package com.county_cars.vroom.modules.marketplace.dto.response;

import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Full listing details for the listing detail page")
public class ListingDetailsResponse {

    @Schema(description = "Listing ID", example = "1")
    private Long id;

    @Schema(description = "Listing status")
    private ListingStatus status;

    @Schema(description = "Asking price", example = "12500.00")
    private BigDecimal price;

    @Schema(description = "Listing description")
    private String description;

    @Schema(description = "Location of the vehicle", example = "Dublin, Ireland")
    private String location;

    @Schema(description = "When the listing was published")
    private Instant publishedAt;

    @Schema(description = "When the listing was created")
    private LocalDateTime createdAt;

    @Schema(description = "True when this listing has been boosted for marketplace visibility")
    private Boolean featured;

    @Schema(description = "Number of days the listing has been active on the marketplace (null if not yet published)")
    private Long daysOnMarket;

    // ── Vehicle info ──────────────────────────────────────────────────────────

    @Schema(description = "Full vehicle information sourced from the Vehicle Passport")
    private VehicleSummaryResponse vehicle;

    // ── Media – vehicle is the single source of truth ─────────────────────────

    @Schema(description = "Primary thumbnail (vehicle.media[displayOrder=1])")
    private VehicleMediaResponse primaryImage;

    @Schema(description = "Full media gallery ordered by display_order, sourced from vehicle.media")
    private List<VehicleMediaResponse> gallery;

    // ── Valuation ─────────────────────────────────────────────────────────────

    @Schema(description = "Most recent valuation snapshot for this vehicle (null if no valuation recorded)")
    private ValuationSummaryResponse valuationSummary;

    // ── Seller info ───────────────────────────────────────────────────────────

    @Schema(description = "Seller basic information")
    private SellerSummaryResponse seller;
}
