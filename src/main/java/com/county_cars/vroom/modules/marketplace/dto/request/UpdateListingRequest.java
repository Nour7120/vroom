package com.county_cars.vroom.modules.marketplace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial-update request for an existing listing.
 * All fields are optional – only non-null values are applied.
 * Only the listing owner may call this endpoint; status must be DRAFT or ACTIVE.
 */
@Data
@Schema(description = "Request to update a listing's price, description, location, or featured flag")
public class UpdateListingRequest {

    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Schema(description = "New asking price (omit to leave unchanged)", example = "11500.00")
    private BigDecimal price;

    @Size(max = 2000)
    @Schema(description = "Updated description (omit to leave unchanged)", example = "Recently serviced, 2 owners")
    private String description;

    @Size(max = 255)
    @Schema(description = "Updated location (omit to leave unchanged)", example = "Cork, Ireland")
    private String location;

    @Schema(description = "Boost visibility flag – only admin/operator should set true (omit to leave unchanged)")
    private Boolean featured;
}

