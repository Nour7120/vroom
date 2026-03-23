package com.county_cars.vroom.modules.garage.dto.response;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "A media item (photo/video) linked to a vehicle")
public class VehicleMediaResponse {

    @Schema(description = "Media entry ID")
    private Long id;

    @Schema(description = "Display order")
    private Integer displayOrder;

    @Schema(description = "Attachment metadata")
    private AttachmentResponse attachment;
}

