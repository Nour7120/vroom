package com.county_cars.vroom.modules.garage.dto.request;

import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Link an already-uploaded attachment to a vehicle as a document")
public class LinkVehicleDocumentRequest {

    @NotNull
    @Schema(description = "ID of the attachment (returned from the upload endpoint)",
            example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long attachmentId;

    @NotNull
    @Schema(description = "Document type", example = "MOT", requiredMode = Schema.RequiredMode.REQUIRED)
    private VehicleDocumentType documentType;
}

