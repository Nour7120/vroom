package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.garage.dto.request.CreateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleResponse;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleOwnership;
import com.county_cars.vroom.modules.garage.mapper.VehicleMapper;
import com.county_cars.vroom.modules.garage.repository.GarageVehicleRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleDocumentRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleMediaRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleOwnershipRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.garage.service.VehicleService;
import com.county_cars.vroom.modules.attachment.service.AttachmentService;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository          vehicleRepository;
    private final VehicleOwnershipRepository ownershipRepository;
    private final VehicleMediaRepository     vehicleMediaRepository;
    private final VehicleDocumentRepository  vehicleDocumentRepository;
    private final GarageVehicleRepository    garageVehicleRepository;
    private final AttachmentService          attachmentService;
    private final VehicleMapper              vehicleMapper;
    private final CurrentUserService         currentUserService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {

        // At least one identifier must be provided
        boolean hasReg = StringUtils.hasText(request.getRegistrationNumber());
        boolean hasVin = StringUtils.hasText(request.getVin());
        if (!hasReg && !hasVin) {
            throw new BadRequestException("registrationNumber or vin must be provided");
        }

        // Duplicate guards
        if (hasReg && vehicleRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new BadRequestException("A vehicle with registration number '"
                    + request.getRegistrationNumber() + "' already exists");
        }
        if (hasVin && vehicleRepository.existsByVin(request.getVin())) {
            throw new BadRequestException("A vehicle with VIN '"
                    + request.getVin() + "' already exists");
        }

        UserProfile owner = currentUserService.getCurrentUserProfile();

        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setOwnerKeycloakId(owner.getKeycloakUserId());
        Vehicle saved = vehicleRepository.save(vehicle);

        // Record initial ownership
        VehicleOwnership ownership = VehicleOwnership.builder()
                .vehicle(saved)
                .owner(owner)
                .ownershipStart(LocalDate.now())
                .isCurrent(Boolean.TRUE)
                .build();
        ownershipRepository.save(ownership);

        log.info("Vehicle {} registered by user {}", saved.getId(), owner.getId());
        return vehicleMapper.toResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));

        if (!vehicle.getOwnerKeycloakId().equals(keycloakId)) {
            throw new UnauthorizedException("You do not own this vehicle");
        }

        vehicleMapper.updateEntity(request, vehicle);
        Vehicle saved = vehicleRepository.save(vehicle);

        log.info("Vehicle {} updated by user {}", vehicleId, keycloakId);
        return vehicleMapper.toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public VehicleResponse getVehicleById(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse findVehicleByRegistration(String registrationNumber) {
        Vehicle vehicle = vehicleRepository.findByRegistrationNumber(registrationNumber)
                .orElseThrow(() -> new NotFoundException(
                        "No vehicle found with registration: " + registrationNumber));
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    public VehicleResponse findVehicleByVin(String vin) {
        Vehicle vehicle = vehicleRepository.findByVin(vin)
                .orElseThrow(() -> new NotFoundException("No vehicle found with VIN: " + vin));
        return vehicleMapper.toResponse(vehicle);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    public List<VehicleResponse> listUserVehicles() {
        UserProfile owner = currentUserService.getCurrentUserProfile();

        List<VehicleOwnership> ownerships = ownershipRepository
                .findAllByOwnerIdAndIsCurrentTrueOrderByOwnershipStartDesc(owner.getId());

        List<Vehicle> vehicles = ownerships.stream()
                .map(VehicleOwnership::getVehicle)
                .toList();

        return vehicleMapper.toResponseList(vehicles);
    }

    // ── Delete (cascade) ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteVehicle(Long vehicleId) {
        String keycloakId = currentUserService.getCurrentKeycloakUserId();

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));

        if (!vehicle.getOwnerKeycloakId().equals(keycloakId)) {
            throw new UnauthorizedException("You do not own this vehicle.");
        }

        // 1. Cascade-delete all media (soft-delete link + physical file removal)
        vehicleMediaRepository.findAllByVehicleId(vehicleId).forEach(media -> {
            media.setIsDeleted(Boolean.TRUE);
            media.setDeletedAt(LocalDateTime.now());
            media.setDeletedBy(keycloakId);
            vehicleMediaRepository.save(media);
            attachmentService.deleteBySystem(media.getAttachment().getId());
        });

        // 2. Cascade-delete all documents (soft-delete link + physical file removal)
        vehicleDocumentRepository.findAllByVehicleId(vehicleId).forEach(doc -> {
            doc.setIsDeleted(Boolean.TRUE);
            doc.setDeletedAt(LocalDateTime.now());
            doc.setDeletedBy(keycloakId);
            vehicleDocumentRepository.save(doc);
            attachmentService.deleteBySystem(doc.getAttachment().getId());
        });

        // 3. Soft-delete garage entries across ALL users who have this vehicle
        garageVehicleRepository.findAllByVehicleId(vehicleId).forEach(entry -> {
            entry.setIsDeleted(Boolean.TRUE);
            entry.setDeletedAt(LocalDateTime.now());
            entry.setDeletedBy(keycloakId);
            garageVehicleRepository.save(entry);
        });

        // 4. Soft-delete the vehicle itself
        vehicle.setIsDeleted(Boolean.TRUE);
        vehicle.setDeletedAt(LocalDateTime.now());
        vehicle.setDeletedBy(keycloakId);
        vehicleRepository.save(vehicle);

        log.info("Vehicle {} soft-deleted with cascade by user {}", vehicleId, keycloakId);
    }
}

