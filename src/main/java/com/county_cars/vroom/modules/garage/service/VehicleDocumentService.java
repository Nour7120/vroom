package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleDocumentRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;

import java.util.List;

public interface VehicleDocumentService {

    /** Link an uploaded attachment to a vehicle as a document (1 per VehicleDocumentType). */
    VehicleDocumentResponse linkDocument(Long vehicleId, LinkVehicleDocumentRequest request);

    /** Soft-delete the document link and physically remove the underlying file. */
    void deleteDocument(Long vehicleId, Long documentId);

    /** List all documents for a vehicle. */
    List<VehicleDocumentResponse> listDocuments(Long vehicleId);
}

