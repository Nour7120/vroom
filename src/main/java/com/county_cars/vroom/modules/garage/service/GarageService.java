package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.AddToGarageRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateGarageCategoryRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleNotesRequest;
import com.county_cars.vroom.modules.garage.dto.response.GarageVehicleResponse;

import java.util.List;

public interface GarageService {

    GarageVehicleResponse addVehicleToGarage(AddToGarageRequest request);

    void removeVehicleFromGarage(Long vehicleId);

    List<GarageVehicleResponse> listUserGarage();

    GarageVehicleResponse updateGarageCategory(UpdateGarageCategoryRequest request);

    GarageVehicleResponse updateVehicleNotes(UpdateVehicleNotesRequest request);
}

