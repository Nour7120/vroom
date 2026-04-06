package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.LinkVehicleMediaRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateMediaDisplayOrderRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleMediaResponse;

import java.util.List;

public interface VehicleMediaService {

    /** Link an uploaded attachment to a vehicle as a media item. */
    VehicleMediaResponse linkMedia(Long vehicleId, LinkVehicleMediaRequest request);

    /** Update the display order of a specific media item (swap if collision). */
    VehicleMediaResponse updateDisplayOrder(Long vehicleId, Long mediaId, UpdateMediaDisplayOrderRequest request);

    /** Soft-delete the media link and physically remove the underlying file. */
    void deleteMedia(Long vehicleId, Long mediaId);

    /** List all media for a vehicle ordered by displayOrder ASC. */
    List<VehicleMediaResponse> listMedia(Long vehicleId);
}

