package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleMediaRequest;
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

    // ── Link ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleMediaResponse linkMedia(Long vehicleId, LinkVehicleMediaRequest request) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        Vehicle vehicle   = requireOwnedVehicle(vehicleId, keycloakId);

        var attachment = attachmentRepository.findById(request.getAttachmentId())
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + request.getAttachmentId()));

        // Validate: caller must own the attachment
        if (!keycloakId.equals(attachment.getCreatedBy())) {
            throw new UnauthorizedException("Attachment id=" + request.getAttachmentId() + " does not belong to you.");
        }

        // Validate: only images and videos are allowed as vehicle media
        String mime = attachment.getContentType();
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
            throw new BadRequestException("Only image or video files can be linked as vehicle media. Detected type: " + mime);
        }

        // Enforce max counts per type
        if (mime.startsWith("image/") && vehicleMediaRepository.countImagesByVehicleId(vehicleId) >= MAX_IMAGES) {
            throw new BadRequestException("Maximum of " + MAX_IMAGES + " images per vehicle reached.");
        }
        if (mime.startsWith("video/") && vehicleMediaRepository.countVideosByVehicleId(vehicleId) >= MAX_VIDEOS) {
            throw new BadRequestException("Maximum of " + MAX_VIDEOS + " videos per vehicle reached.");
        }

        // Resolve display order
        int order = resolveDisplayOrder(vehicleId, request.getDisplayOrder());

        VehicleMedia media = VehicleMedia.builder()
                .vehicle(vehicle)
                .attachment(attachment)
                .displayOrder(order)
                .build();

        VehicleMedia saved = vehicleMediaRepository.save(media);

        // Mark attachment as linked
        attachmentService.markAsLinked(attachment.getId(), keycloakId);

        log.info("Media linked: vehicleId={} attachmentId={} displayOrder={}", vehicleId, attachment.getId(), order);
        return garageMapper.toMediaResponse(saved);
    }

    // ── Update display order ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleMediaResponse updateDisplayOrder(Long vehicleId, Long mediaId, UpdateMediaDisplayOrderRequest request) {
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

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteMedia(Long vehicleId, Long mediaId) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        requireOwnedVehicle(vehicleId, keycloakId);

        VehicleMedia media = vehicleMediaRepository.findByIdAndVehicleId(mediaId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Media item not found: " + mediaId));

        Long attachmentId = media.getAttachment().getId();

        // Soft-delete the media link
        media.setIsDeleted(Boolean.TRUE);
        media.setDeletedAt(LocalDateTime.now());
        media.setDeletedBy(keycloakId);
        vehicleMediaRepository.save(media);

        // Physically delete the file and soft-delete the attachment record
        attachmentService.deleteBySystem(attachmentId);

        log.info("Media deleted: vehicleId={} mediaId={} attachmentId={}", vehicleId, mediaId, attachmentId);
    }

    // ── List ─────────────────────────────────────────────────────────────────────

    @Override
    public List<VehicleMediaResponse> listMedia(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle not found: " + vehicleId);
        }
        return garageMapper.toMediaResponseList(
                vehicleMediaRepository.findAllByVehicleIdOrderByDisplayOrderAsc(vehicleId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Vehicle requireOwnedVehicle(Long vehicleId, String keycloakId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));
        if (!keycloakId.equals(vehicle.getOwnerKeycloakId())) {
            throw new UnauthorizedException("You do not own vehicle: " + vehicleId);
        }
        return vehicle;
    }

    /**
     * If displayOrder was provided and is ≥1, use it directly.
     * Otherwise append after the last existing item (or start at 1).
     */
    private int resolveDisplayOrder(Long vehicleId, Integer requested) {
        if (requested != null && requested >= 1) return requested;
        List<VehicleMedia> existing = vehicleMediaRepository.findAllByVehicleIdOrderByDisplayOrderAsc(vehicleId);
        if (existing.isEmpty()) return 1;
        int maxOrder = existing.stream()
                .mapToInt(m -> m.getDisplayOrder() == null ? 0 : m.getDisplayOrder())
                .max()
                .orElse(0);
        return maxOrder + 1;
    }
}

