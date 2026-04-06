package com.county_cars.vroom.modules.garage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Link an already-uploaded attachment to a vehicle as a media item")
public class LinkVehicleMediaRequest {

    @NotNull
    @Schema(description = "ID of the attachment (returned from the upload endpoint)",
            example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long attachmentId;

    @Min(1)
    @Schema(description = "1-based display order. Position 1 becomes the thumbnail. " +
            "If omitted the item is appended after the last existing position.",
            example = "1")
    private Integer displayOrder;
}

