package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * A single media item (image or video) sourced directly from the vehicle's media store.
 * This is the canonical source of truth — it is NOT a listing-level attachment.
 */
@Data
@Schema(description = "Vehicle media item (image or video) sourced from the vehicle – single source of truth")
public class VehicleMediaResponse {

    @Schema(description = "Attachment ID", example = "7")
    private Long attachmentId;

    @Schema(description = "Stored file name (UUID-based)", example = "a1b2c3d4.jpg")
    private String fileName;

    @Schema(description = "Original file name uploaded by the user", example = "front_view.jpg")
    private String originalFileName;

    @Schema(description = "MIME content type", example = "image/jpeg")
    private String contentType;

    @Schema(description = "File size in bytes", example = "204800")
    private Long fileSize;

    @Schema(description = "Display order within the vehicle's media collection (1 = primary thumbnail)", example = "1")
    private Integer displayOrder;

    @Schema(description = "Derived media type: IMAGE or VIDEO", example = "IMAGE",
            allowableValues = {"IMAGE", "VIDEO"})
    private String mediaType;
}

