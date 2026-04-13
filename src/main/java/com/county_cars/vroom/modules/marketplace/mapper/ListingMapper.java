package com.county_cars.vroom.modules.marketplace.mapper;

import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import com.county_cars.vroom.modules.garage.entity.VehicleValuationHistory;
import com.county_cars.vroom.modules.marketplace.dto.response.*;
import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ListingMapper {

    // ── Summary (browse page) ─────────────────────────────────────────────────
    // primaryImageId / primaryImageFileName are NOT mapped here – they are
    // batch-enriched from vehicle.media in the service layer to avoid N+1.
    // daysOnMarket is computed in the service layer.

    @Mapping(target = "listingId",                  source = "id")
    @Mapping(target = "vehicleMake",                source = "vehicle.make")
    @Mapping(target = "vehicleModel",               source = "vehicle.model")
    @Mapping(target = "vehicleYearOfManufacture",   source = "vehicle.yearOfManufacture")
    @Mapping(target = "vehicleCurrentMileage",      source = "vehicle.currentMileage")
    @Mapping(target = "featured",                   source = "featured")
    @Mapping(target = "primaryImageId",             ignore = true)   // set by service from vehicle.media
    @Mapping(target = "primaryImageFileName",       ignore = true)   // set by service from vehicle.media
    @Mapping(target = "daysOnMarket",               ignore = true)   // computed in service
    ListingSummaryResponse toSummaryResponse(Listing listing);

    // ── Details (listing detail page) ─────────────────────────────────────────
    // Vehicle and seller are delegated automatically to toVehicleSummary / toSellerSummary.
    // Media fields are populated in the service from vehicle.media (single source of truth).

    @Mapping(target = "featured",          source = "featured")
    @Mapping(target = "primaryImage",      ignore = true)   // populated in service from vehicle.media
    @Mapping(target = "gallery",           ignore = true)   // populated in service from vehicle.media
    @Mapping(target = "daysOnMarket",      ignore = true)   // computed in service
    @Mapping(target = "valuationSummary",  ignore = true)   // loaded in service from vehicle_valuation_history
    ListingDetailsResponse toDetailsResponse(Listing listing);

    // ── Nested mappings ───────────────────────────────────────────────────────

    VehicleSummaryResponse toVehicleSummary(Vehicle vehicle);

    @Mapping(target = "id",          source = "id")
    @Mapping(target = "displayName", source = "displayName")
    SellerSummaryResponse toSellerSummary(UserProfile profile);

    // ── Vehicle media → VehicleMediaResponse (canonical path) ─────────────────
    // Media is owned by Vehicle; listings read it, never write to it.

    @Mapping(target = "attachmentId",     source = "attachment.id")
    @Mapping(target = "fileName",         source = "attachment.fileName")
    @Mapping(target = "originalFileName", source = "attachment.originalFileName")
    @Mapping(target = "contentType",      source = "attachment.contentType")
    @Mapping(target = "fileSize",         source = "attachment.fileSize")
    @Mapping(target = "displayOrder",     source = "displayOrder")
    @Mapping(target = "mediaType",        expression = "java(vehicleMedia.getAttachment().getContentType() != null && vehicleMedia.getAttachment().getContentType().startsWith(\"video/\") ? \"VIDEO\" : \"IMAGE\")")
    VehicleMediaResponse toVehicleMediaResponse(VehicleMedia vehicleMedia);

    List<VehicleMediaResponse> toVehicleMediaResponses(List<VehicleMedia> vehicleMediaList);

    // ── Valuation ─────────────────────────────────────────────────────────────

    @Mapping(target = "privateSaleValue",    source = "privateSaleValue")
    @Mapping(target = "averageMarketValue",  source = "averageMarketValue")
    @Mapping(target = "dealerRetailValue",   source = "dealerRetailValue")
    @Mapping(target = "tradeInValue",        source = "tradeInValue")
    @Mapping(target = "valuationConfidence", source = "valuationConfidence")
    @Mapping(target = "valuationDate",       source = "valuationDate")
    ValuationSummaryResponse toValuationSummaryResponse(VehicleValuationHistory valuation);
}
