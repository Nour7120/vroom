package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleDocumentRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleDocument;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.repository.VehicleDocumentRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.garage.service.VehicleDocumentService;
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
public class VehicleDocumentServiceImpl implements VehicleDocumentService {

    private final VehicleRepository         vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final AttachmentRepository      attachmentRepository;
    private final AttachmentService         attachmentService;
    private final GarageMapper              garageMapper;
    private final CurrentUserService        currentUserService;

    // ── Link ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleDocumentResponse linkDocument(Long vehicleId, LinkVehicleDocumentRequest request) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        Vehicle vehicle   = requireOwnedVehicle(vehicleId, keycloakId);

        var attachment = attachmentRepository.findById(request.getAttachmentId())
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + request.getAttachmentId()));

        // Validate: caller must own the attachment
        if (!keycloakId.equals(attachment.getCreatedBy())) {
            throw new UnauthorizedException("Attachment id=" + request.getAttachmentId() + " does not belong to you.");
        }

        // Validate: document attachments must NOT be images or videos
        String mime = attachment.getContentType();
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            throw new BadRequestException("Images and videos cannot be linked as vehicle documents. Detected type: " + mime);
        }

        // Enforce: 1 document per VehicleDocumentType
        if (documentRepository.existsByVehicleIdAndDocumentType(vehicleId, request.getDocumentType())) {
            throw new BadRequestException("A document of type " + request.getDocumentType()
                    + " already exists for this vehicle. Delete the existing one before adding a new one.");
        }

        VehicleDocument document = VehicleDocument.builder()
                .vehicle(vehicle)
                .attachment(attachment)
                .documentType(request.getDocumentType())
                .build();

        VehicleDocument saved = documentRepository.save(document);

        // Mark attachment as linked
        attachmentService.markAsLinked(attachment.getId(), keycloakId);

        log.info("Document linked: vehicleId={} attachmentId={} type={}", vehicleId, attachment.getId(), request.getDocumentType());
        return garageMapper.toDocumentResponse(saved);
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteDocument(Long vehicleId, Long documentId) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();
        requireOwnedVehicle(vehicleId, keycloakId);

        VehicleDocument document = documentRepository.findByIdAndVehicleId(documentId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));

        Long attachmentId = document.getAttachment().getId();

        // Soft-delete the document link
        document.setIsDeleted(Boolean.TRUE);
        document.setDeletedAt(LocalDateTime.now());
        document.setDeletedBy(keycloakId);
        documentRepository.save(document);

        // Physically delete the file and soft-delete the attachment record
        attachmentService.deleteBySystem(attachmentId);

        log.info("Document deleted: vehicleId={} documentId={} attachmentId={}", vehicleId, documentId, attachmentId);
    }

    // ── List ─────────────────────────────────────────────────────────────────────

    @Override
    public List<VehicleDocumentResponse> listDocuments(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle not found: " + vehicleId);
        }
        return garageMapper.toDocumentResponseList(documentRepository.findAllByVehicleId(vehicleId));
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
}

