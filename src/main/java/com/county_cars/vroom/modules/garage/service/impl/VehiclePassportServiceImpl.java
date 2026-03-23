package com.county_cars.vroom.modules.garage.service.impl;

import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.garage.dto.response.VehiclePassportResponse;
import com.county_cars.vroom.modules.garage.entity.*;
import com.county_cars.vroom.modules.garage.mapper.GarageMapper;
import com.county_cars.vroom.modules.garage.repository.*;
import com.county_cars.vroom.modules.garage.service.VehiclePassportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehiclePassportServiceImpl implements VehiclePassportService {

    private final VehicleRepository               vehicleRepository;
    private final VehicleMileageHistoryRepository mileageHistoryRepository;
    private final VehicleMotHistoryRepository     motHistoryRepository;
    private final VehicleValuationHistoryRepository valuationHistoryRepository;
    private final VehicleOwnershipRepository      ownershipRepository;
    private final VehicleDocumentRepository       documentRepository;
    private final VehicleMediaRepository          mediaRepository;
    private final GarageMapper                    garageMapper;

    @Override
    public VehiclePassportResponse getVehiclePassport(Long vehicleId) {
        // 1. Load vehicle identity — fail fast if not found
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + vehicleId));

        // 2. Query each table individually — no large object graphs
        List<VehicleMileageHistory>   mileageHistory  = mileageHistoryRepository.findAllByVehicleIdOrderByRecordedDateDesc(vehicleId);
        List<VehicleMotHistory>       motHistory      = motHistoryRepository.findAllByVehicleIdOrderByTestDateDesc(vehicleId);
        List<VehicleValuationHistory> valuations      = valuationHistoryRepository.findAllByVehicleIdOrderByValuationDateDesc(vehicleId);
        List<VehicleOwnership>        ownership       = ownershipRepository.findAllByVehicleIdOrderByOwnershipStartDesc(vehicleId);
        List<VehicleDocument>         documents       = documentRepository.findAllByVehicleId(vehicleId);
        List<VehicleMedia>            media           = mediaRepository.findAllByVehicleIdOrderByDisplayOrderAsc(vehicleId);

        // 3. Assemble passport in the service layer
        VehiclePassportResponse passport = new VehiclePassportResponse();

        // Identity
        passport.setVehicleId(vehicle.getId());
        passport.setRegistrationNumber(vehicle.getRegistrationNumber());
        passport.setVin(vehicle.getVin());
        passport.setMake(vehicle.getMake());
        passport.setModel(vehicle.getModel());
        passport.setVariant(vehicle.getVariant());
        passport.setYearOfManufacture(vehicle.getYearOfManufacture());
        passport.setFuelType(vehicle.getFuelType());
        passport.setTransmission(vehicle.getTransmission());
        passport.setEngineCapacity(vehicle.getEngineCapacity());
        passport.setColour(vehicle.getColour());
        passport.setNumberOfDoors(vehicle.getNumberOfDoors());
        passport.setBodyType(vehicle.getBodyType());
        passport.setCo2Emissions(vehicle.getCo2Emissions());

        // Snapshot
        passport.setCurrentMileage(vehicle.getCurrentMileage());
        passport.setFirstRegistrationDate(vehicle.getFirstRegistrationDate());
        passport.setPreviousOwners(vehicle.getPreviousOwners());
        passport.setMotExpiryDate(vehicle.getMotExpiryDate());
        passport.setTaxExpiryDate(vehicle.getTaxExpiryDate());

        // History
        passport.setMileageHistory(garageMapper.toMileageEntries(mileageHistory));
        passport.setMotHistory(garageMapper.toMotEntries(motHistory));
        passport.setValuationHistory(garageMapper.toValuationEntries(valuations));
        passport.setOwnershipTimeline(garageMapper.toOwnershipResponseList(ownership));
        passport.setDocuments(garageMapper.toDocumentResponseList(documents));
        passport.setMedia(garageMapper.toMediaResponseList(media));

        log.info("Vehicle passport assembled for vehicleId={}", vehicleId);
        return passport;
    }
}

