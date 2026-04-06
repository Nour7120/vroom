package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleDocumentRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;
import com.county_cars.vroom.modules.garage.service.VehicleDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manages documents (MOT, insurance, service records, etc.) linked to a vehicle.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Upload the file: {@code POST /api/v1/attachments} → get {@code attachmentId}</li>
 *   <li>Link it here:    {@code POST /api/v1/vehicles/{vehicleId}/documents}</li>
 * </ol>
 *
 * <p>Only 1 document per {@code VehicleDocumentType} is allowed per vehicle.</p>
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/documents")
@RequiredArgsConstructor
@Tag(name = "Vehicle Documents", description = "Link and remove documents for a vehicle (MOT, insurance, etc.)")
public class VehicleDocumentController {

    private final VehicleDocumentService vehicleDocumentService;

    @PostMapping
    @Operation(summary = "Link an uploaded attachment to a vehicle as a document")
    public ResponseEntity<VehicleDocumentResponse> linkDocument(
            @PathVariable Long vehicleId,
            @Valid @RequestBody LinkVehicleDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleDocumentService.linkDocument(vehicleId, request));
    }

    @GetMapping
    @Operation(summary = "List all documents linked to a vehicle")
    public ResponseEntity<List<VehicleDocumentResponse>> listDocuments(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleDocumentService.listDocuments(vehicleId));
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a document — soft-deletes the link and physically removes the file")
    public void deleteDocument(@PathVariable Long vehicleId, @PathVariable Long documentId) {
        vehicleDocumentService.deleteDocument(vehicleId, documentId);
    }
}

