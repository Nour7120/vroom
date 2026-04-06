package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.UpdateMediaDisplayOrderRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleMediaResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VehicleMediaService {

    /**
     * Uploads a media file and links it to the vehicle in a single coordinated operation.
     * The file must be an image or video.
     *
     * @param vehicleId    target vehicle (must be owned by the current user)
     * @param file         the image / video file to upload
     * @param displayOrder optional 1-based display position; appended at the end when {@code null}
     */
    VehicleMediaResponse linkMedia(Long vehicleId, MultipartFile file, Integer displayOrder);

    /** Update the display order of a specific media item (swap if collision). */
    VehicleMediaResponse updateDisplayOrder(Long vehicleId, Long mediaId, UpdateMediaDisplayOrderRequest request);

    /** Soft-delete the media link and physically remove the underlying file. */
    void deleteMedia(Long vehicleId, Long mediaId);

    /** List all media for a vehicle ordered by displayOrder ASC. */
    List<VehicleMediaResponse> listMedia(Long vehicleId);
}
