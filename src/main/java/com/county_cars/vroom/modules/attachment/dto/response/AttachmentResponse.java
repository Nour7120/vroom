package com.county_cars.vroom.modules.attachment.dto.response;

import com.county_cars.vroom.modules.attachment.entity.AttachmentStatus;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.entity.StorageProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Attachment metadata response")
public class AttachmentResponse {

    @Schema(description = "Attachment database ID", example = "1")
    private Long id;

    @Schema(description = "Generated (UUID-based) file name", example = "a1b2c3d4.pdf")
    private String fileName;

    @Schema(description = "Original file name uploaded by user", example = "driving_license.pdf")
    private String originalFileName;

    @Schema(description = "MIME content type", example = "image/jpeg")
    private String contentType;

    @Schema(description = "File size in bytes", example = "204800")
    private Long fileSize;

    @Schema(description = "Visibility", example = "PRIVATE")
    private AttachmentVisibility visibility;

    @Schema(description = "Lifecycle status", example = "UPLOADED")
    private AttachmentStatus status;

    @Schema(description = "Storage provider", example = "LOCAL")
    private StorageProvider storageProvider;

    @Schema(description = "Who uploaded (keycloakUserId from JPA auditing)", example = "a3f1-...")
    private String createdBy;

    @Schema(description = "Upload timestamp")
    private LocalDateTime createdAt;
}
