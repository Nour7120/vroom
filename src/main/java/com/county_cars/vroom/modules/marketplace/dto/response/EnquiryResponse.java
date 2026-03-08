package com.county_cars.vroom.modules.marketplace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Confirmation that an enquiry was submitted")
public class EnquiryResponse {

    @Schema(description = "Enquiry ID", example = "10")
    private Long id;

    @Schema(description = "Listing ID the enquiry was sent for", example = "1")
    private Long listingId;

    @Schema(description = "Message sent by the buyer")
    private String message;

    @Schema(description = "Who submitted the enquiry (keycloak user id from auditing)")
    private String createdBy;

    @Schema(description = "When the enquiry was submitted")
    private LocalDateTime createdAt;
}

