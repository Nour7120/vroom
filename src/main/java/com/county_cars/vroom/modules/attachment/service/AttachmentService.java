package com.county_cars.vroom.modules.attachment.service;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {

    /**
     * Validates, stores and persists a file attachment.
     * Category is no longer a concern of the attachment — it is implied by the
     * junction table (vehicle_media, vehicle_document, etc.) that links it.
     *
     * <p>Validation performed:
     * <ul>
     *   <li>File size limits (10 MB images / 100 MB videos / 25 MB documents)</li>
     *   <li>Allowed extension whitelist</li>
     *   <li>Magic-number (file signature) check via Apache Tika</li>
     * </ul>
     */
    AttachmentResponse upload(MultipartFile file, AttachmentVisibility visibility);

    /**
     * Downloads the file, enforcing visibility rules.
     */
    Resource download(Long id);

    /**
     * Soft-deletes the attachment record and removes the physical file from storage.
     * Only owner or ADMIN may delete.
     */
    void delete(Long id);

    /**
     * System-level delete: bypasses ownership check.
     * Used by cascade operations (e.g. when a vehicle is deleted).
     * Soft-deletes the DB record and physically removes the file from storage.
     */
    void deleteBySystem(Long id);

    /**
     * Marks an attachment as LINKED, validating that the caller is the owner.
     * Called by junction services (VehicleMediaService, VehicleDocumentService)
     * after successfully creating the link record.
     */
    void markAsLinked(Long id, String ownerKeycloakId);
}
