package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleDocument;
import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.repository.VehicleDocumentRepository;
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
public class VehicleDocumentServiceImpl implements com.county_cars.vroom.modules.garage.service.VehicleDocumentService {

    private final VehicleRepository       vehicleRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final AttachmentRepository    attachmentRepository;
    private final AttachmentService       attachmentService;
    private final GarageMapper            garageMapper;
    private final CurrentUserService      currentUserService;

    // ── Upload + Link (calling-module orchestration) ──────────────────────────

    /**
     * Uploads a document file, creates the {@code vehicle_document} link record
     * and marks the attachment as LINKED — all coordinated by this service.
     *
     * <p>Orchestration:
     * <ol>
     *   <li>{@code attachmentService.upload} — REQUIRES_NEW, commits immediately.</li>
     *   <li>Fetch {@link Attachment} entity in the outer transaction.</li>
     *   <li>Persist {@link VehicleDocument} link record.</li>
     *   <li>{@code markAsLinked} — participates in the outer transaction.</li>
     *   <li>On any failure: {@code deleteOrphan} — REQUIRES_NEW, commits cleanup.</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public VehicleDocumentResponse linkDocument(Long vehicleId, MultipartFile file,
                                                VehicleDocumentType documentType) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        Vehicle vehicle   = requireOwnedVehicle(vehicleId, keycloakId);

        // ── Step 1: Upload (REQUIRES_NEW — commits independently) ──────────────
        AttachmentResponse attResp = attachmentService.upload(file, AttachmentVisibility.PRIVATE);

        try {
            // ── Step 2: Fetch entity in the outer transaction ───────────────────
            Attachment attachment = attachmentRepository.findById(attResp.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Attachment not found after upload: " + attResp.getId()));

            // ── Step 3: Create link record ──────────────────────────────────────
            VehicleDocument document = VehicleDocument.builder()
                    .vehicle(vehicle)
                    .attachment(attachment)
                    .documentType(documentType)
                    .build();
            VehicleDocument saved = vehicleDocumentRepository.save(document);

            // ── Step 4: Mark as LINKED (participates in outer tx) ───────────────
            attachmentService.markAsLinked(attachment.getId());

            log.info("Document linked: vehicleId={} attachmentId={} type={}",
                    vehicleId, attachment.getId(), documentType);
            return garageMapper.toDocumentResponse(saved);

        } catch (Exception e) {
            // ── Step 5: Cleanup on failure (REQUIRES_NEW — commits independently) ─
            attachmentService.deleteOrphan(attResp.getId());
            log.warn("Document link failed; orphan attachment cleaned up: id={}", attResp.getId());
            throw e;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteDocument(Long vehicleId, Long documentId) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        requireOwnedVehicle(vehicleId, keycloakId);

        VehicleDocument document = vehicleDocumentRepository.findByIdAndVehicleId(documentId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));

        Long attachmentId = document.getAttachment().getId();

        document.setIsDeleted(Boolean.TRUE);
        document.setDeletedAt(LocalDateTime.now());
        document.setDeletedBy(keycloakId);
        vehicleDocumentRepository.save(document);

        attachmentService.deleteBySystem(attachmentId);
        log.info("Document deleted: vehicleId={} documentId={} attachmentId={}",
                vehicleId, documentId, attachmentId);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    public List<VehicleDocumentResponse> listDocuments(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle not found: " + vehicleId);
        }
        return garageMapper.toDocumentResponseList(
                vehicleDocumentRepository.findAllByVehicleId(vehicleId));
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
}
