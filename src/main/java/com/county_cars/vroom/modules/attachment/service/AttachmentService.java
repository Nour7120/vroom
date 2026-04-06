package com.county_cars.vroom.modules.attachment.service;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {

    /**
     * Validates the file, writes it to storage and persists the attachment record
     * (status = {@code UPLOADED}).
     *
     * <p><b>Transaction: REQUIRES_NEW.</b>  The attachment is committed in its own
     * independent transaction so that the calling module's catch block can find and
     * clean it up via {@link #deleteOrphan} even when the caller's transaction is
     * being rolled back.</p>
     *
     * @deprecated Uploading a raw attachment without immediately linking it leaves an
     *             orphaned record.  Prefer calling this method from a module-specific
     *             service that immediately links the result and calls
     *             {@link #markAsLinked(Long)} on success or {@link #deleteOrphan(Long)}
     *             on failure.
     */
    @Deprecated
    AttachmentResponse upload(MultipartFile file, AttachmentVisibility visibility);

    /**
     * Transitions the attachment's status from {@code UPLOADED} to {@code LINKED}.
     *
     * <p>Called by the owning module service <em>after</em> the junction record
     * (e.g. {@code vehicle_media}, {@code vehicle_document}) has been persisted
     * successfully in the same transaction.</p>
     *
     * <p><b>Transaction: REQUIRED</b> — participates in the calling transaction.</p>
     */
    void markAsLinked(Long id);

    /**
     * Cleans up an attachment that was uploaded but whose link step failed.
     *
     * <p>Soft-deletes the DB record and deletes the physical file.</p>
     *
     * <p><b>Transaction: REQUIRES_NEW.</b>  Runs in a completely independent
     * transaction so the cleanup is committed regardless of the caller's transaction
     * outcome.  Because {@link #upload} also uses REQUIRES_NEW the attachment row is
     * already committed and visible to this transaction.</p>
     */
    void deleteOrphan(Long id);

    /**
     * Downloads the file, enforcing visibility rules.
     */
    Resource download(Long id);

    /**
     * Soft-deletes the attachment record and removes the physical file from storage.
     * Only the owner or an ADMIN may call this.
     */
    void delete(Long id);

    /**
     * System-level delete: bypasses ownership check.
     * Used by cascade operations (e.g. when a vehicle or user is deleted).
     * Participates in the caller's transaction (REQUIRED).
     */
    void deleteBySystem(Long id);
}
