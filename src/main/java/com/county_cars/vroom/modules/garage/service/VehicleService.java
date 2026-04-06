package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.CreateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleResponse;

import java.util.List;

public interface VehicleService {

    VehicleResponse createVehicle(CreateVehicleRequest request);

    VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request);

    VehicleResponse getVehicleById(Long vehicleId);

    VehicleResponse findVehicleByRegistration(String registrationNumber);

    VehicleResponse findVehicleByVin(String vin);

    List<VehicleResponse> listUserVehicles();

    /**
     * Soft-deletes the vehicle and cascades:
     * – soft-deletes all VehicleMedia links
     * – soft-deletes all VehicleDocument links
     * – physically removes the underlying attachment files from storage
     * – soft-deletes all GarageVehicle entries for this vehicle
     * Only the current owner may call this.
     */
    void deleteVehicle(Long vehicleId);
}

