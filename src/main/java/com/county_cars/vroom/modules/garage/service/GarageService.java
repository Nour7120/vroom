package com.county_cars.vroom.modules.garage.service;

import com.county_cars.vroom.modules.garage.dto.request.AddToGarageRequest;
import com.county_cars.vroom.modules.garage.dto.request.AddVehicleWithDetailsRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateGarageCategoryRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleNotesRequest;
import com.county_cars.vroom.modules.garage.dto.response.GarageVehicleResponse;

import java.util.List;

public interface GarageService {

    GarageVehicleResponse addVehicleToGarage(AddToGarageRequest request);

    /**
     * Atomically creates a new vehicle (+ ownership record) and adds it
     * to the current user's garage in a single transaction.
     */
    GarageVehicleResponse createVehicleAndAddToGarage(AddVehicleWithDetailsRequest request);

    void removeVehicleFromGarage(Long vehicleId);

    List<GarageVehicleResponse> listUserGarage();

    GarageVehicleResponse updateGarageCategory(UpdateGarageCategoryRequest request);

    GarageVehicleResponse updateVehicleNotes(UpdateVehicleNotesRequest request);
}

