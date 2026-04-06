package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.response.VehicleDocumentResponse;
import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import com.county_cars.vroom.modules.garage.service.VehicleDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages documents (MOT, insurance, registration…) linked to a vehicle.
 *
 * <p>Single-step upload flow:
 * POST /api/v1/vehicles/{vehicleId}/documents  (multipart/form-data)
 * </p>
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/documents")
@RequiredArgsConstructor
@Tag(name = "Vehicle Documents", description = "Upload and manage documents for a vehicle")
public class VehicleDocumentController {

    private final VehicleDocumentService vehicleDocumentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload and link a document to a vehicle",
        description = """
            Uploads a document file and immediately links it to the vehicle.
            Upload, validation and linking happen in a single coordinated operation.
            If linking fails the uploaded file is cleaned up automatically.

            - Allowed types: pdf, doc, docx, jpg, jpeg, png (max 25 MB)
            - `documentType` must be one of: MOT, INSURANCE, REGISTRATION, SERVICE_HISTORY, OTHER

            **Requires a valid Bearer JWT and vehicle ownership.**
            """
    )
    public ResponseEntity<VehicleDocumentResponse> uploadDocument(
            @PathVariable Long vehicleId,
            @Parameter(description = "Document file", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Semantic document type", required = true)
            @RequestParam("documentType") VehicleDocumentType documentType) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleDocumentService.linkDocument(vehicleId, file, documentType));
    }

    @GetMapping
    @Operation(summary = "List all documents for a vehicle")
    public ResponseEntity<List<VehicleDocumentResponse>> listDocuments(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleDocumentService.listDocuments(vehicleId));
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a document — soft-deletes the link and deletes the file")
    public void deleteDocument(@PathVariable Long vehicleId, @PathVariable Long documentId) {
        vehicleDocumentService.deleteDocument(vehicleId, documentId);
    }
}
