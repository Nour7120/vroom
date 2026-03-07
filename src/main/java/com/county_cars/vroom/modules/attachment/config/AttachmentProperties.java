package com.county_cars.vroom.modules.attachment.config;

import com.county_cars.vroom.modules.attachment.entity.AttachmentCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Externalized configuration for the attachment module.
 *
 * <p>All values are bound from {@code attachment.*} properties in
 * {@code application.properties} (or any active profile override).
 * Hardcoded constants have been removed from service code.</p>
 *
 * <p>Example configuration:
 * <pre>
 * attachment.max-image-size-bytes=10485760
 * attachment.max-document-size-bytes=26214400
 * attachment.image-categories=PROFILE_PHOTO,VEHICLE_IMAGE
 * attachment.allowed-extensions=jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,mp4,mov,avi
 * attachment.allowed-mime-types=image/jpeg,image/png,image/gif,image/webp,application/pdf,\
 *   application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,\
 *   application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,\
 *   video/mp4,video/quicktime,video/x-msvideo
 * </pre>
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "attachment")
public class AttachmentProperties {

    /** Maximum file size in bytes for image-type categories (default 10 MB). */
    private long maxImageSizeBytes = 10_485_760L;

    /** Maximum file size in bytes for document-type categories (default 25 MB). */
    private long maxDocumentSizeBytes = 26_214_400L;

    /**
     * Categories considered "image" for the purpose of size-limit selection.
     * Anything not in this list uses the document size limit.
     */
    private List<AttachmentCategory> imageCategories = List.of(
            AttachmentCategory.PROFILE_PHOTO,
            AttachmentCategory.VEHICLE_IMAGE
    );

    /**
     * Whitelist of allowed file extensions (lower-cased, no dot).
     * Rejection is fast-path before the more expensive Tika MIME check.
     */
    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf",
            "doc", "docx",
            "xls", "xlsx",
            "mp4", "mov", "avi"
    );

    /**
     * Whitelist of MIME types that Tika is allowed to detect from the file bytes.
     * If Tika resolves a type not on this list, the upload is rejected.
     */
    private List<String> allowedMimeTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "video/mp4",
            "video/quicktime",
            "video/x-msvideo"
    );
}

