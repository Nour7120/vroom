package com.county_cars.vroom.modules.marketplace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request to add images to a listing")
public class AddListingImagesRequest {

    @NotEmpty(message = "At least one attachment ID is required")
    @Size(max = 5, message = "Maximum 5 images per listing")
    @Schema(description = "List of attachment IDs to add (max 5 total across all uploads)", example = "[1, 2, 3]")
    private List<Long> attachmentIds;
}

