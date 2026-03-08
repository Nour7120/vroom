package com.county_cars.vroom.modules.marketplace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to submit a buyer enquiry on a listing")
public class CreateEnquiryRequest {

    @NotBlank
    @Size(max = 1000)
    @Schema(description = "Message to the seller", example = "Is the car still available? Can I arrange a viewing?")
    private String message;
}

