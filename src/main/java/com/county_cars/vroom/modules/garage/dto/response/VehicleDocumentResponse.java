package com.county_cars.vroom.modules.garage.dto.response;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "A document linked to a vehicle")
public class VehicleDocumentResponse {

    @Schema(description = "Document entry ID")
    private Long id;

    @Schema(description = "Document type")
    private VehicleDocumentType documentType;

    @Schema(description = "Attachment metadata")
    private AttachmentResponse attachment;
}

