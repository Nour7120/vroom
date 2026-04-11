package com.county_cars.vroom.modules.garage.controller;

import com.county_cars.vroom.modules.garage.dto.response.VehiclePassportResponse;
import com.county_cars.vroom.modules.garage.service.VehiclePassportService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
//@Hidden
@Tag(name = "Vehicle Passport", description = "Retrieve aggregated vehicle passport data")
public class VehiclePassportController {

    private final VehiclePassportService vehiclePassportService;

    @GetMapping("/{vehicleId}/passport")
    @Operation(summary = "Get the full Vehicle Passport — identity, history, documents, media, and ownership timeline")
    public ResponseEntity<VehiclePassportResponse> getPassport(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehiclePassportService.getVehiclePassport(vehicleId));
    }
}

