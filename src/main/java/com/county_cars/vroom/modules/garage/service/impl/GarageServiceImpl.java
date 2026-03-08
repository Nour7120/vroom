package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.garage.dto.request.AddToGarageRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateGarageCategoryRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleNotesRequest;
import com.county_cars.vroom.modules.garage.dto.response.GarageVehicleResponse;
import com.county_cars.vroom.modules.garage.entity.GarageVehicle;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.repository.GarageVehicleRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.garage.service.GarageService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GarageServiceImpl implements GarageService {

    private final GarageVehicleRepository garageVehicleRepository;
    private final VehicleRepository       vehicleRepository;
    private final GarageMapper            garageMapper;
    private final CurrentUserService      currentUserService;

    @Override
    @Transactional
    public GarageVehicleResponse addVehicleToGarage(AddToGarageRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + request.getVehicleId()));

        if (garageVehicleRepository.existsByUserIdAndVehicleId(user.getId(), vehicle.getId())) {
            throw new BadRequestException("Vehicle is already in your garage");
        }

        GarageVehicle entry = GarageVehicle.builder()
                .user(user)
                .vehicle(vehicle)
                .category(request.getCategory())
                .notes(request.getNotes())
                .build();

        GarageVehicle saved = garageVehicleRepository.save(entry);
        log.info("Vehicle {} added to garage for user {}", vehicle.getId(), user.getId());
        return garageMapper.toGarageVehicleResponse(saved);
    }

    @Override
    @Transactional
    public void removeVehicleFromGarage(Long vehicleId) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        GarageVehicle entry = garageVehicleRepository.findByUserIdAndVehicleId(user.getId(), vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found in your garage: " + vehicleId));

        entry.setIsDeleted(Boolean.TRUE);
        entry.setDeletedAt(LocalDateTime.now());
        entry.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        garageVehicleRepository.save(entry);
        log.info("Vehicle {} removed from garage for user {}", vehicleId, user.getId());
    }

    @Override
    public List<GarageVehicleResponse> listUserGarage() {
        UserProfile user = currentUserService.getCurrentUserProfile();
        List<GarageVehicle> entries = garageVehicleRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        return garageMapper.toGarageVehicleResponseList(entries);
    }

    @Override
    @Transactional
    public GarageVehicleResponse updateGarageCategory(UpdateGarageCategoryRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        GarageVehicle entry = garageVehicleRepository
                .findByUserIdAndVehicleId(user.getId(), request.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found in your garage: " + request.getVehicleId()));

        entry.setCategory(request.getCategory());
        return garageMapper.toGarageVehicleResponse(garageVehicleRepository.save(entry));
    }

    @Override
    @Transactional
    public GarageVehicleResponse updateVehicleNotes(UpdateVehicleNotesRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        GarageVehicle entry = garageVehicleRepository
                .findByUserIdAndVehicleId(user.getId(), request.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found in your garage: " + request.getVehicleId()));

        entry.setNotes(request.getNotes());
        return garageMapper.toGarageVehicleResponse(garageVehicleRepository.save(entry));
    }
}

