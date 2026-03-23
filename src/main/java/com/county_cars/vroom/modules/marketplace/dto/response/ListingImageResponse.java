package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Image attached to a listing with its display order")
public class ListingImageResponse {

    @Schema(description = "Attachment ID", example = "7")
    private Long attachmentId;

    @Schema(description = "File name", example = "a1b2c3.jpg")
    private String fileName;

    @Schema(description = "Original file name", example = "front_view.jpg")
    private String originalFileName;

    @Schema(description = "Display order, starting at 1", example = "1")
    private Integer displayOrder;
}

