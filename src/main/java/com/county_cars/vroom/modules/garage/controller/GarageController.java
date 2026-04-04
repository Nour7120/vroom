package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.request.AddToGarageRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateGarageCategoryRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleNotesRequest;
import com.county_cars.vroom.modules.garage.dto.response.GarageVehicleResponse;
import com.county_cars.vroom.modules.garage.service.GarageService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/garage")
@RequiredArgsConstructor
//@Hidden
@Tag(name = "Digital Garage", description = "Manage the user's personal vehicle garage")
public class GarageController {

    private final GarageService garageService;

    @GetMapping
    @Operation(summary = "List all vehicles in the current user's garage")
    public ResponseEntity<List<GarageVehicleResponse>> listGarage() {
        return ResponseEntity.ok(garageService.listUserGarage());
    }

    @PostMapping
    @Operation(summary = "Add a vehicle to the current user's garage")
    public ResponseEntity<GarageVehicleResponse> addToGarage(
            @Valid @RequestBody AddToGarageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(garageService.addVehicleToGarage(request));
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Remove a vehicle from the current user's garage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromGarage(@PathVariable Long vehicleId) {
        garageService.removeVehicleFromGarage(vehicleId);
    }

    @PatchMapping("/category")
    @Operation(summary = "Update the garage category (OWNED / WISHLIST) for a vehicle")
    public ResponseEntity<GarageVehicleResponse> updateCategory(
            @Valid @RequestBody UpdateGarageCategoryRequest request) {
        return ResponseEntity.ok(garageService.updateGarageCategory(request));
    }

    @PatchMapping("/notes")
    @Operation(summary = "Update personal notes for a garage vehicle")
    public ResponseEntity<GarageVehicleResponse> updateNotes(
            @Valid @RequestBody UpdateVehicleNotesRequest request) {
        return ResponseEntity.ok(garageService.updateVehicleNotes(request));
    }
}

