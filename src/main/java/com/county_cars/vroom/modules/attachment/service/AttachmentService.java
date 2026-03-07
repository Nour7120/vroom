package com.county_cars.vroom.modules.attachment.service;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.AttachmentCategory;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {

    /**
     * Validates, stores and persists a file attachment.
     *
     * <p>Validation performed:
     * <ul>
     *   <li>File size limits (10 MB images / 25 MB documents)</li>
     *   <li>Allowed extension whitelist</li>
     *   <li>Magic-number (file signature) check</li>
     * </ul>
     */
    AttachmentResponse upload(MultipartFile file,
                              AttachmentCategory category,
                              AttachmentVisibility visibility);

    /**
     * Downloads the file, enforcing visibility rules.
     *
     * <p>Rules:
     * <ul>
     *   <li>PUBLIC     → any authenticated user</li>
     *   <li>PRIVATE    → owner (createdBy == current user) or ADMIN</li>
     *   <li>ADMIN_ONLY → ADMIN role only</li>
     * </ul>
     *
     * @return Spring {@link Resource} suitable for streaming
     */
    Resource download(Long id);

    /**
     * Soft-deletes the attachment record and removes the physical file from storage.
     * Only owner or ADMIN may delete.
     */
    void delete(Long id);
}
