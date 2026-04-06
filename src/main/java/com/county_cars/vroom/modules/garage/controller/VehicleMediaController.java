package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleMediaRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateMediaDisplayOrderRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleMediaResponse;
import com.county_cars.vroom.modules.garage.service.VehicleMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manages media (images / videos) linked to a vehicle.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Upload the file: {@code POST /api/v1/attachments} → get {@code attachmentId}</li>
 *   <li>Link it here:    {@code POST /api/v1/vehicles/{vehicleId}/media}</li>
 * </ol>
 *
 * <p>The item with {@code displayOrder = 1} is always used as the vehicle thumbnail.</p>
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/media")
@RequiredArgsConstructor
@Tag(name = "Vehicle Media", description = "Link, reorder and remove photos/videos for a vehicle")
public class VehicleMediaController {

    private final VehicleMediaService vehicleMediaService;

    @PostMapping
    @Operation(summary = "Link an uploaded attachment to a vehicle as a media item")
    public ResponseEntity<VehicleMediaResponse> linkMedia(
            @PathVariable Long vehicleId,
            @Valid @RequestBody LinkVehicleMediaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleMediaService.linkMedia(vehicleId, request));
    }

    @GetMapping
    @Operation(summary = "List all media items for a vehicle ordered by displayOrder")
    public ResponseEntity<List<VehicleMediaResponse>> listMedia(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleMediaService.listMedia(vehicleId));
    }

    @PatchMapping("/{mediaId}/order")
    @Operation(summary = "Update the display order of a media item — setting order=1 makes it the thumbnail")
    public ResponseEntity<VehicleMediaResponse> updateOrder(
            @PathVariable Long vehicleId,
            @PathVariable Long mediaId,
            @Valid @RequestBody UpdateMediaDisplayOrderRequest request) {
        return ResponseEntity.ok(vehicleMediaService.updateDisplayOrder(vehicleId, mediaId, request));
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a media item — soft-deletes the link and physically removes the file")
    public void deleteMedia(@PathVariable Long vehicleId, @PathVariable Long mediaId) {
        vehicleMediaService.deleteMedia(vehicleId, mediaId);
    }
}

