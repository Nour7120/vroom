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

    @Schema(description = "Vehicle information")
    private VehicleSummaryResponse vehicle;

    @Schema(description = "Seller basic information")
    private SellerSummaryResponse seller;

    @Schema(description = "Images ordered by display_order")
    private List<ListingImageResponse> images;
}

