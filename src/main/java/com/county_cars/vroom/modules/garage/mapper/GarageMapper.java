package com.county_cars.vroom.modules.garage.mapper;

import com.county_cars.vroom.modules.attachment.mapper.AttachmentMapper;
import com.county_cars.vroom.modules.garage.dto.response.*;
import com.county_cars.vroom.modules.garage.entity.*;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {AttachmentMapper.class})
public interface GarageMapper {

    // ── GarageVehicle → response ──────────────────────────────────────────────

    @Mapping(target = "vehicleId",           source = "vehicle.id")
    @Mapping(target = "registrationNumber",  source = "vehicle.registrationNumber")
    @Mapping(target = "make",                source = "vehicle.make")
    @Mapping(target = "model",               source = "vehicle.model")
    @Mapping(target = "variant",             source = "vehicle.variant")
    @Mapping(target = "yearOfManufacture",   source = "vehicle.yearOfManufacture")
    @Mapping(target = "fuelType",            source = "vehicle.fuelType")
    @Mapping(target = "transmission",        source = "vehicle.transmission")
    @Mapping(target = "colour",              source = "vehicle.colour")
    @Mapping(target = "currentMileage",      source = "vehicle.currentMileage")
    @Mapping(target = "motExpiryDate",       source = "vehicle.motExpiryDate")
    @Mapping(target = "taxExpiryDate",       source = "vehicle.taxExpiryDate")
    GarageVehicleResponse toGarageVehicleResponse(GarageVehicle garageVehicle);

    List<GarageVehicleResponse> toGarageVehicleResponseList(List<GarageVehicle> list);

    // ── VehicleOwnership → response ───────────────────────────────────────────

    @Mapping(target = "ownerId",          source = "owner.id")
    @Mapping(target = "ownerDisplayName", source = "owner.displayName")
    VehicleOwnershipResponse toOwnershipResponse(VehicleOwnership ownership);

    List<VehicleOwnershipResponse> toOwnershipResponseList(List<VehicleOwnership> list);

    // ── VehicleDocument → response ────────────────────────────────────────────

    @Mapping(target = "attachment", source = "attachment")
    VehicleDocumentResponse toDocumentResponse(VehicleDocument document);

    List<VehicleDocumentResponse> toDocumentResponseList(List<VehicleDocument> list);

    // ── VehicleMedia → response ───────────────────────────────────────────────

    @Mapping(target = "attachment", source = "attachment")
    VehicleMediaResponse toMediaResponse(VehicleMedia media);

    List<VehicleMediaResponse> toMediaResponseList(List<VehicleMedia> list);

    // ── History entry mappings ────────────────────────────────────────────────

    VehiclePassportResponse.MileageHistoryEntry toMileageEntry(VehicleMileageHistory h);

    List<VehiclePassportResponse.MileageHistoryEntry> toMileageEntries(List<VehicleMileageHistory> list);

    VehiclePassportResponse.MotHistoryEntry toMotEntry(VehicleMotHistory h);

    List<VehiclePassportResponse.MotHistoryEntry> toMotEntries(List<VehicleMotHistory> list);

    VehiclePassportResponse.ValuationHistoryEntry toValuationEntry(VehicleValuationHistory h);

    List<VehiclePassportResponse.ValuationHistoryEntry> toValuationEntries(List<VehicleValuationHistory> list);
}

