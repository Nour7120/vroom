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
}

