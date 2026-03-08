package com.county_cars.vroom.modules.marketplace.mapper;

import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.marketplace.dto.response.*;
import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.marketplace.entity.ListingAttachment;
import com.county_cars.vroom.modules.marketplace.entity.ListingEnquiry;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ListingMapper {

    // ── Summary (browse page) ─────────────────────────────────────────────────

    @Mapping(target = "listingId",                  source = "id")
    @Mapping(target = "vehicleMake",                source = "vehicle.make")
    @Mapping(target = "vehicleModel",               source = "vehicle.model")
    @Mapping(target = "vehicleYearOfManufacture",   source = "vehicle.yearOfManufacture")
    @Mapping(target = "vehicleCurrentMileage",      source = "vehicle.currentMileage")
    @Mapping(target = "primaryImageId",             source = "primaryImage.id")
    @Mapping(target = "primaryImageFileName",       source = "primaryImage.fileName")
    ListingSummaryResponse toSummaryResponse(Listing listing);

    // ── Details (listing detail page) ─────────────────────────────────────────
    // vehicle and seller are delegated automatically to toVehicleSummary / toSellerSummary

    @Mapping(target = "images", ignore = true)   // populated manually in service
    ListingDetailsResponse toDetailsResponse(Listing listing);

    // ── Nested mappings ───────────────────────────────────────────────────────

    VehicleSummaryResponse toVehicleSummary(Vehicle vehicle);

    @Mapping(target = "id",          source = "id")
    @Mapping(target = "displayName", source = "displayName")
    SellerSummaryResponse toSellerSummary(UserProfile profile);

    // ── Listing attachment → image response ───────────────────────────────────

    @Mapping(target = "attachmentId",     source = "attachment.id")
    @Mapping(target = "fileName",         source = "attachment.fileName")
    @Mapping(target = "originalFileName", source = "attachment.originalFileName")
    @Mapping(target = "displayOrder",     source = "displayOrder")
    ListingImageResponse toImageResponse(ListingAttachment listingAttachment);

    List<ListingImageResponse> toImageResponses(List<ListingAttachment> attachments);

    // ── Enquiry ───────────────────────────────────────────────────────────────

    @Mapping(target = "listingId", source = "listing.id")
    EnquiryResponse toEnquiryResponse(ListingEnquiry enquiry);
}


