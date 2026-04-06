package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.garage.dto.request.AddToGarageRequest;
import com.county_cars.vroom.modules.garage.dto.request.AddVehicleWithDetailsRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateGarageCategoryRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleNotesRequest;
import com.county_cars.vroom.modules.garage.dto.response.GarageVehicleResponse;
import com.county_cars.vroom.modules.garage.entity.GarageVehicle;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import com.county_cars.vroom.modules.garage.entity.VehicleOwnership;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.mapper.VehicleMapper;
import com.county_cars.vroom.modules.garage.repository.GarageVehicleRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleMediaRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleOwnershipRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.garage.service.GarageService;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GarageServiceImpl implements GarageService {

    private final GarageVehicleRepository garageVehicleRepository;
    private final VehicleRepository       vehicleRepository;
    private final VehicleOwnershipRepository ownershipRepository;
    private final VehicleMediaRepository  vehicleMediaRepository;
    private final GarageMapper            garageMapper;
    private final VehicleMapper           vehicleMapper;
    private final CurrentUserService      currentUserService;

    // ── Add existing vehicle to garage ────────────────────────────────────────

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
        return enrich(garageMapper.toGarageVehicleResponse(saved), saved.getVehicle().getId());
    }

    // ── Create vehicle + add to garage (atomic) ───────────────────────────────

    @Override
    @Transactional
    public GarageVehicleResponse createVehicleAndAddToGarage(AddVehicleWithDetailsRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();
        var vehicleReq   = request.getVehicle();

        // At least one identifier required
        boolean hasReg = StringUtils.hasText(vehicleReq.getRegistrationNumber());
        boolean hasVin = StringUtils.hasText(vehicleReq.getVin());
        if (!hasReg && !hasVin) {
            throw new BadRequestException("registrationNumber or vin must be provided");
        }

        if (hasReg && vehicleRepository.existsByRegistrationNumber(vehicleReq.getRegistrationNumber())) {
            throw new BadRequestException("A vehicle with registration '" + vehicleReq.getRegistrationNumber() + "' already exists");
        }
        if (hasVin && vehicleRepository.existsByVin(vehicleReq.getVin())) {
            throw new BadRequestException("A vehicle with VIN '" + vehicleReq.getVin() + "' already exists");
        }

        // Save vehicle
        Vehicle vehicle = vehicleMapper.toEntity(vehicleReq);
        vehicle.setOwnerKeycloakId(user.getKeycloakUserId());
        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        // Record initial ownership
        ownershipRepository.save(VehicleOwnership.builder()
                .vehicle(savedVehicle)
                .owner(user)
                .ownershipStart(LocalDate.now())
                .isCurrent(Boolean.TRUE)
                .build());

        // Add to garage
        GarageVehicle entry = GarageVehicle.builder()
                .user(user)
                .vehicle(savedVehicle)
                .category(request.getGarageCategory())
                .notes(request.getNotes())
                .build();

        GarageVehicle saved = garageVehicleRepository.save(entry);
        log.info("Vehicle {} created and added to garage for user {}", savedVehicle.getId(), user.getId());
        return enrich(garageMapper.toGarageVehicleResponse(saved), savedVehicle.getId());
    }

    // ── Remove from garage ────────────────────────────────────────────────────

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

    // ── List garage (enriched with thumbnail + mediaCount) ────────────────────

    @Override
    public List<GarageVehicleResponse> listUserGarage() {
        UserProfile user = currentUserService.getCurrentUserProfile();
        List<GarageVehicle> entries = garageVehicleRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());

        if (entries.isEmpty()) return List.of();

        List<Long> vehicleIds = entries.stream()
                .map(e -> e.getVehicle().getId())
                .toList();

        // Batch-fetch thumbnails (displayOrder=1) for all vehicles in one query
        Map<Long, Long> thumbnailMap = vehicleMediaRepository
                .findThumbnailsByVehicleIds(vehicleIds)
                .stream()
                .collect(Collectors.toMap(
                        vm -> vm.getVehicle().getId(),
                        vm -> vm.getAttachment().getId()
                ));

        // Batch-fetch media counts
        Map<Long, Long> countMap = vehicleMediaRepository
                .countByVehicleIds(vehicleIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return entries.stream().map(entry -> {
            GarageVehicleResponse response = garageMapper.toGarageVehicleResponse(entry);
            Long vid = entry.getVehicle().getId();
            response.setThumbnailAttachmentId(thumbnailMap.get(vid));
            response.setMediaCount(countMap.getOrDefault(vid, 0L).intValue());
            return response;
        }).toList();
    }

    // ── Update category / notes ───────────────────────────────────────────────

    @Override
    @Transactional
    public GarageVehicleResponse updateGarageCategory(UpdateGarageCategoryRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        GarageVehicle entry = garageVehicleRepository
                .findByUserIdAndVehicleId(user.getId(), request.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found in your garage: " + request.getVehicleId()));

        entry.setCategory(request.getCategory());
        GarageVehicle saved = garageVehicleRepository.save(entry);
        return enrich(garageMapper.toGarageVehicleResponse(saved), saved.getVehicle().getId());
    }

    @Override
    @Transactional
    public GarageVehicleResponse updateVehicleNotes(UpdateVehicleNotesRequest request) {
        UserProfile user = currentUserService.getCurrentUserProfile();

        GarageVehicle entry = garageVehicleRepository
                .findByUserIdAndVehicleId(user.getId(), request.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found in your garage: " + request.getVehicleId()));

        entry.setNotes(request.getNotes());
        GarageVehicle saved = garageVehicleRepository.save(entry);
        return enrich(garageMapper.toGarageVehicleResponse(saved), saved.getVehicle().getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Enrich a single GarageVehicleResponse with thumbnail + mediaCount. */
    private GarageVehicleResponse enrich(GarageVehicleResponse response, Long vehicleId) {
        vehicleMediaRepository.findByVehicleIdAndDisplayOrder(vehicleId, 1)
                .ifPresent(vm -> response.setThumbnailAttachmentId(vm.getAttachment().getId()));

        List<VehicleMedia> allMedia = vehicleMediaRepository.findAllByVehicleId(vehicleId);
        response.setMediaCount(allMedia.size());
        return response;
    }
}

