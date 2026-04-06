package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.garage.dto.request.UpdateMediaDisplayOrderRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleMediaResponse;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.repository.VehicleMediaRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleMediaServiceImpl implements com.county_cars.vroom.modules.garage.service.VehicleMediaService {

    private static final int MAX_IMAGES = 30;
    private static final int MAX_VIDEOS = 3;

    private final VehicleRepository      vehicleRepository;
    private final VehicleMediaRepository vehicleMediaRepository;
    private final AttachmentRepository   attachmentRepository;
    private final AttachmentService      attachmentService;
    private final GarageMapper           garageMapper;
    private final CurrentUserService     currentUserService;

    // ── Upload + Link (calling-module orchestration) ──────────────────────────

    /**
     * Uploads a media file, validates it, creates the {@code vehicle_media} link record
     * and marks the attachment as LINKED — all in a coordinated flow where this service
     * is the sole orchestrator.
     *
     * <p>Orchestration:
     * <ol>
     *   <li>{@code attachmentService.upload} — REQUIRES_NEW, commits immediately.</li>
     *   <li>Fetch {@link Attachment} entity in the outer transaction.</li>
     *   <li>MIME + count validation.</li>
     *   <li>Persist {@link VehicleMedia} link record.</li>
     *   <li>{@code markAsLinked} — participates in the outer transaction.</li>
     *   <li>On any failure: {@code deleteOrphan} — REQUIRES_NEW, commits cleanup.</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public VehicleMediaResponse linkMedia(Long vehicleId, MultipartFile file, Integer displayOrder) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        Vehicle vehicle   = requireOwnedVehicle(vehicleId, keycloakId);

        // ── Step 1: Upload (REQUIRES_NEW — commits independently) ──────────────
        AttachmentResponse attResp = attachmentService.upload(file, AttachmentVisibility.PUBLIC);

        try {
            // ── Step 2: Fetch entity in the outer transaction ───────────────────
            Attachment attachment = attachmentRepository.findById(attResp.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Attachment not found after upload: " + attResp.getId()));

            // ── Step 3: Validate MIME ───────────────────────────────────────────
            String mime = attachment.getContentType();
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                throw new BadRequestException(
                        "Only image or video files can be linked as vehicle media. Detected: " + mime);
            }
            if (mime.startsWith("image/") && vehicleMediaRepository.countImagesByVehicleId(vehicleId) >= MAX_IMAGES) {
                throw new BadRequestException("Maximum of " + MAX_IMAGES + " images per vehicle reached.");
            }
            if (mime.startsWith("video/") && vehicleMediaRepository.countVideosByVehicleId(vehicleId) >= MAX_VIDEOS) {
                throw new BadRequestException("Maximum of " + MAX_VIDEOS + " videos per vehicle reached.");
            }

            // ── Step 4: Create link record ──────────────────────────────────────
            int order = resolveDisplayOrder(vehicleId, displayOrder);
            VehicleMedia media = VehicleMedia.builder()
                    .vehicle(vehicle)
                    .attachment(attachment)
                    .displayOrder(order)
                    .build();
            VehicleMedia saved = vehicleMediaRepository.save(media);

            // ── Step 5: Mark as LINKED (participates in outer tx) ───────────────
            attachmentService.markAsLinked(attachment.getId());

            log.info("Media linked: vehicleId={} attachmentId={} displayOrder={}",
                    vehicleId, attachment.getId(), order);
            return garageMapper.toMediaResponse(saved);

        } catch (Exception e) {
            // ── Step 6: Cleanup on failure (REQUIRES_NEW — commits independently) ─
            attachmentService.deleteOrphan(attResp.getId());
            log.warn("Media link failed; orphan attachment cleaned up: id={}", attResp.getId());
            throw e;
        }
    }

    // ── Update display order ──────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleMediaResponse updateDisplayOrder(Long vehicleId, Long mediaId,
                                                   UpdateMediaDisplayOrderRequest request) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        requireOwnedVehicle(vehicleId, keycloakId);

        VehicleMedia target = vehicleMediaRepository.findByIdAndVehicleId(mediaId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Media item not found: " + mediaId));

        int newOrder = request.getDisplayOrder();

        // Swap with the item that currently occupies the desired order (if any)
        vehicleMediaRepository.findByVehicleIdAndDisplayOrder(vehicleId, newOrder)
                .filter(other -> !other.getId().equals(mediaId))
                .ifPresent(other -> {
                    other.setDisplayOrder(target.getDisplayOrder());
                    vehicleMediaRepository.save(other);
                });

        target.setDisplayOrder(newOrder);
        VehicleMedia saved = vehicleMediaRepository.save(target);

        log.info("Media display order updated: mediaId={} newOrder={}", mediaId, newOrder);
        return garageMapper.toMediaResponse(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteMedia(Long vehicleId, Long mediaId) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        requireOwnedVehicle(vehicleId, keycloakId);

        VehicleMedia media = vehicleMediaRepository.findByIdAndVehicleId(mediaId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Media item not found: " + mediaId));

        Long attachmentId = media.getAttachment().getId();

        media.setIsDeleted(Boolean.TRUE);
        media.setDeletedAt(LocalDateTime.now());
        media.setDeletedBy(keycloakId);
        vehicleMediaRepository.save(media);

        attachmentService.deleteBySystem(attachmentId);
        log.info("Media deleted: vehicleId={} mediaId={} attachmentId={}", vehicleId, mediaId, attachmentId);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    public List<VehicleMediaResponse> listMedia(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle not found: " + vehicleId);
        }
        return garageMapper.toMediaResponseList(
                vehicleMediaRepository.findAllByVehicleIdOrderByDisplayOrderAsc(vehicleId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Vehicle requireOwnedVehicle(Long vehicleId, String keycloakId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));
        if (!keycloakId.equals(vehicle.getOwnerKeycloakId())) {
            throw new UnauthorizedException("You do not own vehicle: " + vehicleId);
        }
        return vehicle;
    }

    private int resolveDisplayOrder(Long vehicleId, Integer requested) {
        if (requested != null && requested >= 1) return requested;
        List<VehicleMedia> existing = vehicleMediaRepository.findAllByVehicleIdOrderByDisplayOrderAsc(vehicleId);
        if (existing.isEmpty()) return 1;
        return existing.stream()
                .mapToInt(m -> m.getDisplayOrder() == null ? 0 : m.getDisplayOrder())
                .max().orElse(0) + 1;
    }
}
