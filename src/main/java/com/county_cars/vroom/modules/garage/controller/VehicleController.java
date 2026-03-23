package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.request.CreateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleResponse;
import com.county_cars.vroom.modules.garage.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Register and manage vehicle identities")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Register a new vehicle")
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createVehicle(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle details — only the current owner may update")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by ID")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    @GetMapping("/registration/{registration}")
    @Operation(summary = "Find vehicle by registration number")
    public ResponseEntity<VehicleResponse> getByRegistration(
            @PathVariable String registration) {
        return ResponseEntity.ok(vehicleService.findVehicleByRegistration(registration));
    }

    @GetMapping("/vin/{vin}")
    @Operation(summary = "Find vehicle by VIN")
    public ResponseEntity<VehicleResponse> getByVin(@PathVariable String vin) {
        return ResponseEntity.ok(vehicleService.findVehicleByVin(vin));
    }

    @GetMapping("/my")
    @Operation(summary = "List all vehicles currently owned by the authenticated user")
    public ResponseEntity<List<VehicleResponse>> listMyVehicles() {
        return ResponseEntity.ok(vehicleService.listUserVehicles());
    }
}

