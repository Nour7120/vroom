package com.county_cars.vroom.modules.attachment.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.config.AttachmentProperties;
import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentStatus;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.entity.StorageProvider;
import com.county_cars.vroom.modules.attachment.mapper.AttachmentMapper;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.attachment.storage.FileStorageService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core attachment service: validates, stores, retrieves and soft-deletes file attachments.
 *
 * <h3>Validation chain (upload)</h3>
 * <ol>
 *   <li>Not-empty check</li>
 *   <li>Extension whitelist — fast-path rejection from {@link AttachmentProperties#getAllowedExtensions()}</li>
 *   <li>File-size limit — image vs document threshold from {@link AttachmentProperties}</li>
 *   <li>Magic-number check — Apache Tika detects actual MIME type from file bytes;
 *       result is compared against {@link AttachmentProperties#getAllowedMimeTypes()}</li>
 * </ol>
 *
 * <h3>Visibility enforcement (download)</h3>
 * <ul>
 *   <li>PUBLIC     — any authenticated user</li>
 *   <li>PRIVATE    — owner ({@code createdBy == currentUserId}) or ADMIN</li>
 *   <li>ADMIN_ONLY — ADMIN role only</li>
 * </ul>
 *
 * <p>All configurable values (size limits, extension whitelist, MIME whitelist,
 * image categories) live in {@link AttachmentProperties} and are externalized
 * via {@code application.properties}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper     attachmentMapper;
    private final FileStorageService   fileStorageService;
    private final CurrentUserService   currentUserService;
    private final AttachmentProperties props;

    @Value("${attachment.storage.provider:local}")
    private String storageProviderKey;

    /** Tika instance is thread-safe and cheap to reuse. */
    private final Tika tika = new Tika();

    // ─── Upload ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AttachmentResponse upload(MultipartFile file, AttachmentVisibility visibility) {

        // 1. Not-empty
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty.");
        }

        String originalName = file.getOriginalFilename() == null
                ? "unknown" : file.getOriginalFilename().trim();
        String extension = extractExtension(originalName);

        // 2. Extension whitelist — fast-path rejection
        if (!props.getAllowedExtensions().contains(extension)) {
            throw new BadRequestException("File extension not allowed: ." + extension
                    + ". Allowed: " + props.getAllowedExtensions());
        }

        // 3. Tika MIME detection — reads only the first few KB
        String detectedMime = detectMime(file);

        // 4. MIME whitelist
        if (!props.getAllowedMimeTypes().contains(detectedMime)) {
            throw new BadRequestException("File content type '" + detectedMime
                    + "' is not allowed. Allowed types: " + props.getAllowedMimeTypes());
        }

        // 5. Size limit — determined by MIME type
        long maxBytes = resolveMaxSize(detectedMime);
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File size " + file.getSize()
                    + " bytes exceeds the limit of " + maxBytes + " bytes.");
        }

        // 6. Store — folder is derived from MIME type, not category
        String folder = resolveStorageFolder(detectedMime);
        String storedFileName = UUID.randomUUID() + "." + extension;
        String storagePath    = fileStorageService.store(file, storedFileName, folder);

        Attachment attachment = Attachment.builder()
                .fileName(storedFileName)
                .originalFileName(originalName)
                .contentType(detectedMime)
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .storageProvider(resolveProvider())
                .visibility(visibility)
                .status(AttachmentStatus.UPLOADED)
                .build();

        Attachment saved = attachmentRepository.save(attachment);
        log.info("Attachment saved: id={} mime={} visibility={} size={}",
                saved.getId(), detectedMime, visibility, file.getSize());

        return attachmentMapper.toResponse(saved);
    }

    // ─── Download ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long id) {
        Attachment attachment = findById(id);
        enforceDownloadAccess(attachment);
        log.info("Downloading attachment id={} path={}", id, attachment.getStoragePath());
        return fileStorageService.load(attachment.getStoragePath());
    }

    // ─── Delete (owner / admin) ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(Long id) {
        Attachment attachment = findById(id);
        String currentUserId = currentUserService.getCurrentKeycloakUserId();

        boolean isAdmin = hasAdminRole();
        boolean isOwner = currentUserId.equals(attachment.getCreatedBy());

        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You are not allowed to delete this attachment.");
        }

        softDeleteAndRemoveFile(attachment, currentUserId);
        log.info("Attachment deleted: id={} by={}", id, currentUserId);
    }

    // ─── Delete (system / cascade) ────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteBySystem(Long id) {
        attachmentRepository.findById(id).ifPresentOrElse(
                attachment -> {
                    softDeleteAndRemoveFile(attachment, "system");
                    log.info("Attachment system-deleted: id={}", id);
                },
                () -> log.warn("deleteBySystem called on missing/already-deleted attachment id={}", id)
        );
    }

    // ─── Mark as linked ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void markAsLinked(Long id, String ownerKeycloakId) {
        Attachment attachment = findById(id);
        if (!ownerKeycloakId.equals(attachment.getCreatedBy())) {
            throw new UnauthorizedException("Attachment id=" + id + " does not belong to you.");
        }
        if (attachment.getStatus() == AttachmentStatus.LINKED) {
            throw new BadRequestException("Attachment id=" + id + " is already linked.");
        }
        attachment.setStatus(AttachmentStatus.LINKED);
        attachmentRepository.save(attachment);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private void softDeleteAndRemoveFile(Attachment attachment, String deletedBy) {
        attachment.setStatus(AttachmentStatus.DELETED);
        attachment.setIsDeleted(Boolean.TRUE);
        attachment.setDeletedAt(LocalDateTime.now());
        attachment.setDeletedBy(deletedBy);
        attachmentRepository.save(attachment);

        try {
            fileStorageService.delete(attachment.getStoragePath());
        } catch (Exception e) {
            log.warn("Physical file deletion failed for attachment id={}: {}",
                    attachment.getId(), e.getMessage());
        }
    }

    private Attachment findById(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));
    }

    private void enforceDownloadAccess(Attachment attachment) {
        AttachmentVisibility visibility = attachment.getVisibility();
        if (visibility == AttachmentVisibility.PUBLIC) return;

        String currentUserId = currentUserService.getCurrentKeycloakUserId();
        boolean isAdmin = hasAdminRole();

        if (visibility == AttachmentVisibility.ADMIN_ONLY) {
            if (!isAdmin) throw new UnauthorizedException("Access denied: admin only.");
            return;
        }

        if (visibility == AttachmentVisibility.PRIVATE) {
            boolean isOwner = currentUserId.equals(attachment.getCreatedBy());
            if (!isOwner && !isAdmin) {
                throw new UnauthorizedException("Access denied: private file.");
            }
        }
    }

    private boolean hasAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ADMIN"));
    }

    private String detectMime(MultipartFile file) {
        try {
            return tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file bytes for MIME validation.", e);
        }
    }

    private long resolveMaxSize(String mime) {
        if (props.getImageMimeTypes().contains(mime)) return props.getMaxImageSizeBytes();
        if (props.getVideoMimeTypes().contains(mime)) return props.getMaxVideoSizeBytes();
        return props.getMaxDocumentSizeBytes();
    }

    /**
     * Maps a detected MIME type to a storage sub-folder.
     * The folder is used for file-system organisation only; it is not persisted.
     */
    private static String resolveStorageFolder(String mime) {
        if (mime.startsWith("image/"))  return "images";
        if (mime.startsWith("video/"))  return "videos";
        return "documents";
    }

    private StorageProvider resolveProvider() {
        return "s3".equalsIgnoreCase(storageProviderKey)
                ? StorageProvider.S3
                : StorageProvider.LOCAL;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BadRequestException("File has no extension: " + filename);
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
