package com.county_cars.vroom.modules.marketplace.dto.response;

import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Schema(description = "Lightweight listing summary for marketplace browse page")
public class ListingSummaryResponse {

    @Schema(description = "Listing ID", example = "1")
    private Long listingId;

    @Schema(description = "Asking price", example = "12500.00")
    private BigDecimal price;

    @Schema(description = "Vehicle make", example = "Toyota")
    private String vehicleMake;

    @Schema(description = "Vehicle model", example = "Corolla")
    private String vehicleModel;

    @Schema(description = "Year of manufacture", example = "2019")
    private Integer vehicleYearOfManufacture;

    @Schema(description = "Listing location", example = "Dublin, Ireland")
    private String location;

    @Schema(description = "Primary image attachment ID")
    private Long primaryImageId;

    @Schema(description = "Primary image file name")
    private String primaryImageFileName;

    @Schema(description = "Listing status")
    private ListingStatus status;

    @Schema(description = "When the listing was published")
    private Instant publishedAt;

    @Schema(description = "When the listing was created")
    private LocalDateTime createdAt;
}

