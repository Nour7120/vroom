package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.response.VehiclePassportResponse;

public interface VehiclePassportService {

    VehiclePassportResponse getVehiclePassport(Long vehicleId);
}

