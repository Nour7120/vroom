package com.county_cars.vroom.modules.attachment.scheduler;

import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentStatus;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically cleans up orphaned attachments whose status is still {@code UPLOADED}.
 *
 * <p>An attachment is orphaned when the calling module's link step failed (or was never
 * attempted) and {@link com.county_cars.vroom.modules.attachment.service.AttachmentService#deleteOrphan}
 * was not called (e.g. the JVM was killed between upload and link).  Because
 * {@link com.county_cars.vroom.modules.attachment.service.AttachmentService#upload} uses
 * {@code REQUIRES_NEW}, the {@code UPLOADED} record is always committed and visible here.</p>
 *
 * <p>Every {@value #INTERVAL_MS} ms this scheduler:
 * <ol>
 *   <li>Finds all non-deleted {@code UPLOADED} attachments.</li>
 *   <li>Deletes the physical file from storage (best-effort).</li>
 *   <li>Soft-deletes the DB record: {@code status = DELETED}, {@code is_deleted = true},
 *       {@code deleted_at = now()}, {@code deleted_by = "scheduler"}.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupScheduler {

    /** 10 minutes in milliseconds. */
    private static final long INTERVAL_MS = 600_000L;

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService   fileStorageService;

    @Scheduled(fixedDelay = INTERVAL_MS)
    @Transactional
    public void cleanUpOrphanedAttachments() {
        List<Attachment> orphans = attachmentRepository.findAllByStatus(AttachmentStatus.UPLOADED);

        if (orphans.isEmpty()) {
            log.debug("Orphan cleanup: no UPLOADED attachments found.");
            return;
        }

        log.info("Orphan cleanup: found {} UPLOADED attachment(s) to clean up.", orphans.size());

        for (Attachment attachment : orphans) {
            // Delete physical file first (best-effort — we need the path before marking DELETED)
            try {
                fileStorageService.delete(attachment.getStoragePath());
            } catch (Exception e) {
                log.warn("Orphan cleanup: physical file deletion failed for attachment id={}: {}",
                        attachment.getId(), e.getMessage());
            }

            // Soft-delete the DB record
            attachment.setStatus(AttachmentStatus.DELETED);
            attachment.setIsDeleted(Boolean.TRUE);
            attachment.setDeletedAt(LocalDateTime.now());
            attachment.setDeletedBy("scheduler");
            attachmentRepository.save(attachment);

            log.info("Orphan cleanup: attachment id={} marked DELETED.", attachment.getId());
        }

        log.info("Orphan cleanup: completed — {} attachment(s) processed.", orphans.size());
    }
}

