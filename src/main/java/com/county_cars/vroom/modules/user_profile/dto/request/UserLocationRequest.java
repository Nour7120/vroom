package com.county_cars.vroom.modules.user_profile.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to create or update a user location")
public class UserLocationRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Location label", example = "Home")
    private String label;

    @Size(max = 255)
    @Schema(description = "Address line 1")
    private String addressLine1;

    @Size(max = 255)
    @Schema(description = "Address line 2")
    private String addressLine2;

    @Size(max = 128)
    @Schema(description = "City", example = "Cairo")
    private String city;

    @Size(max = 128)
    @Schema(description = "Country", example = "Egypt")
    private String country;

    @Schema(description = "Latitude", example = "30.0444")
    private BigDecimal latitude;

    @Schema(description = "Longitude", example = "31.2357")
    private BigDecimal longitude;

    @Schema(description = "Mark as primary location", example = "true")
    private Boolean isPrimary = Boolean.FALSE;
}

