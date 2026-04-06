package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.request.UpdateMediaDisplayOrderRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleMediaResponse;
import com.county_cars.vroom.modules.garage.service.VehicleMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages media (images / videos) linked to a vehicle.
 *
 * <p>Single-step upload flow:
 * POST /api/v1/vehicles/{vehicleId}/media  (multipart/form-data)
 *
 * <p>The item with displayOrder = 1 is used as the vehicle thumbnail.</p>
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/media")
@RequiredArgsConstructor
@Tag(name = "Vehicle Media", description = "Upload, reorder and remove photos/videos for a vehicle")
public class VehicleMediaController {

    private final VehicleMediaService vehicleMediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload and link a media file to a vehicle",
        description = """
            Uploads an image or video file and immediately links it to the vehicle.
            Upload, validation and linking happen in a single coordinated operation.
            If linking fails the uploaded file is cleaned up automatically.

            - Allowed: images (jpg, jpeg, png, gif, webp) and videos (mp4, mov, avi)
            - Max 10 MB per image · Max 100 MB per video
            - Max 30 images and 3 videos per vehicle
            - Position 1 = thumbnail; omit `displayOrder` to append at the end

            **Requires a valid Bearer JWT and vehicle ownership.**
            """
    )
    public ResponseEntity<VehicleMediaResponse> uploadMedia(
            @PathVariable Long vehicleId,
            @Parameter(description = "Image or video file", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "1-based display order (optional)")
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleMediaService.linkMedia(vehicleId, file, displayOrder));
    }

    @GetMapping
    @Operation(summary = "List all media items for a vehicle ordered by displayOrder")
    public ResponseEntity<List<VehicleMediaResponse>> listMedia(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleMediaService.listMedia(vehicleId));
    }

    @PatchMapping("/{mediaId}/order")
    @Operation(summary = "Update the display order of a media item")
    public ResponseEntity<VehicleMediaResponse> updateOrder(
            @PathVariable Long vehicleId,
            @PathVariable Long mediaId,
            @Valid @RequestBody UpdateMediaDisplayOrderRequest request) {
        return ResponseEntity.ok(vehicleMediaService.updateDisplayOrder(vehicleId, mediaId, request));
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a media item — soft-deletes the link and deletes the file")
    public void deleteMedia(@PathVariable Long vehicleId, @PathVariable Long mediaId) {
        vehicleMediaService.deleteMedia(vehicleId, mediaId);
    }
}
