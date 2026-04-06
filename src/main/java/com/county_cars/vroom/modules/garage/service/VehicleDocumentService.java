package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;
import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VehicleDocumentService {

    /**
     * Uploads a document file and links it to the vehicle in a single coordinated operation.
     *
     * @param vehicleId    target vehicle (must be owned by the current user)
     * @param file         the document file (PDF, DOC, DOCX, image)
     * @param documentType the semantic type of this document (MOT, INSURANCE, etc.)
     */
    VehicleDocumentResponse linkDocument(Long vehicleId, MultipartFile file, VehicleDocumentType documentType);

    /** Soft-deletes the document link and physically removes the underlying file. */
    void deleteDocument(Long vehicleId, Long documentId);

    /** List all documents for a vehicle. */
    List<VehicleDocumentResponse> listDocuments(Long vehicleId);
}
