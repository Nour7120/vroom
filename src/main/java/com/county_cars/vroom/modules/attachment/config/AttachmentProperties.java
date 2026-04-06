package com.county_cars.vroom.modules.attachment.config;

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

    /** Maximum file size in bytes for image-type uploads (default 10 MB). */
    private long maxImageSizeBytes = 10_485_760L;

    /** Maximum file size in bytes for video-type uploads (default 100 MB). */
    private long maxVideoSizeBytes = 104_857_600L;

    /** Maximum file size in bytes for document-type uploads (default 25 MB). */
    private long maxDocumentSizeBytes = 26_214_400L;

    /**
     * MIME types considered "image" for size-limit selection.
     * Anything matching image/* that is not here will still be caught by the
     * allowedMimeTypes whitelist.
     */
    private List<String> imageMimeTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * MIME types considered "video" for size-limit selection.
     */
    private List<String> videoMimeTypes = List.of(
            "video/mp4",
            "video/quicktime",
            "video/x-msvideo"
    );

    /**
     * Whitelist of allowed file extensions (lower-cased, no dot).
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
