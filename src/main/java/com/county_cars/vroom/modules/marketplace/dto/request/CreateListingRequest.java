package com.county_cars.vroom.modules.marketplace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to create a new marketplace listing")
public class CreateListingRequest {

    @NotNull
    @Schema(description = "ID of the vehicle to list", example = "1")
    private Long vehicleId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Schema(description = "Asking price", example = "12500.00")
    private BigDecimal price;

    @Size(max = 2000)
    @Schema(description = "Listing description", example = "Well maintained, one owner")
    private String description;

    @Size(max = 255)
    @Schema(description = "Location of the vehicle", example = "Dublin, Ireland")
    private String location;
}

