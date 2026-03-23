package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Seller basic info embedded in listing details")
public class SellerSummaryResponse {

    @Schema(description = "Seller user profile ID", example = "5")
    private Long id;

    @Schema(description = "Seller display name", example = "John Doe")
    private String displayName;
}

