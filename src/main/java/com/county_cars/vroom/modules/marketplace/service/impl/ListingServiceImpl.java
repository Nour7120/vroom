package com.county_cars.vroom.modules.marketplace.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import com.county_cars.vroom.modules.garage.repository.VehicleMediaRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.garage.repository.VehicleValuationHistoryRepository;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.UpdateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingDetailsResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingSummaryResponse;
import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import com.county_cars.vroom.modules.marketplace.mapper.ListingMapper;
import com.county_cars.vroom.modules.marketplace.repository.ListingRepository;
import com.county_cars.vroom.modules.marketplace.repository.ListingSearchRepository;
import com.county_cars.vroom.modules.marketplace.service.ListingService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingServiceImpl implements ListingService {

    private final ListingRepository                 listingRepository;
    private final ListingSearchRepository           listingSearchRepository;
    private final VehicleRepository                 vehicleRepository;
    private final VehicleMediaRepository            vehicleMediaRepository;
    private final VehicleValuationHistoryRepository vehicleValuationHistoryRepository;
    private final ListingMapper                     listingMapper;
    private final CurrentUserService                currentUserService;

    // ── Seller operations ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public ListingDetailsResponse createListing(CreateListingRequest request) {
        UserProfile seller = currentUserService.getCurrentUserProfile();

        Vehicle vehicle = vehicleRepository
                .findByIdAndOwnerKeycloakId(request.getVehicleId(), seller.getKeycloakUserId())
                .orElseThrow(() -> new NotFoundException(
                        "Vehicle not found or does not belong to the current user: " + request.getVehicleId()));

        // Guard: prevent duplicate active/draft listings for the same vehicle
        if (listingRepository.existsByVehicleIdAndSellerIdAndStatusIn(
                vehicle.getId(), seller.getId(),
                List.of(ListingStatus.ACTIVE, ListingStatus.DRAFT))) {
            throw new BadRequestException(
                    "An active or draft listing already exists for vehicle: " + request.getVehicleId());
        }

        Listing listing = Listing.builder()
                .vehicle(vehicle)
                .seller(seller)
                .price(request.getPrice())
                .description(request.getDescription())
                .location(request.getLocation())
                .status(ListingStatus.DRAFT)
                .build();

        Listing saved = listingRepository.save(listing);
        log.info("Listing created: id={}, vehicleId={}, seller={}",
                saved.getId(), vehicle.getId(), seller.getId());

        return buildDetails(saved);
    }

    @Override
    @Transactional
    public ListingDetailsResponse updateListing(Long listingId, UpdateListingRequest request) {
        Listing listing = requireOwnedListing(listingId);

        if (listing.getStatus() == ListingStatus.SOLD
                || listing.getStatus() == ListingStatus.WITHDRAWN
                || listing.getStatus() == ListingStatus.EXPIRED) {
            throw new BadRequestException(
                    "Cannot update a listing with status: " + listing.getStatus());
        }

        if (request.getPrice()       != null) listing.setPrice(request.getPrice());
        if (request.getDescription() != null) listing.setDescription(request.getDescription());
        if (request.getLocation()    != null) listing.setLocation(request.getLocation());
        if (request.getFeatured()    != null) listing.setFeatured(request.getFeatured());

        Listing saved = listingRepository.save(listing);
        log.info("Listing updated: id={}", listingId);

        return buildDetails(saved);
    }

    @Override
    @Transactional
    public ListingDetailsResponse publishListing(Long listingId) {
        Listing listing = requireOwnedListing(listingId);

        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new BadRequestException(
                    "Only a DRAFT listing can be published. Current status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.ACTIVE);
        listing.setPublishedAt(Instant.now());

        Listing saved = listingRepository.save(listing);
        log.info("Listing published: id={}", listingId);

        return buildDetails(saved);
    }

    @Override
    @Transactional
    public ListingDetailsResponse markListingSold(Long listingId) {
        Listing listing = requireOwnedListing(listingId);

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BadRequestException(
                    "Only an ACTIVE listing can be marked as sold. Current status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.SOLD);

        Listing saved = listingRepository.save(listing);
        log.info("Listing marked as SOLD: id={}", listingId);

        return buildDetails(saved);
    }

    @Override
    @Transactional
    public ListingDetailsResponse withdrawListing(Long listingId) {
        Listing listing = requireOwnedListing(listingId);

        if (listing.getStatus() != ListingStatus.ACTIVE && listing.getStatus() != ListingStatus.DRAFT) {
            throw new BadRequestException(
                    "Only an ACTIVE or DRAFT listing can be withdrawn. Current status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.WITHDRAWN);

        Listing saved = listingRepository.save(listing);
        log.info("Listing withdrawn: id={}", listingId);

        return buildDetails(saved);
    }

    @Override
    public Page<ListingSummaryResponse> getMyListings(Pageable pageable) {
        UserProfile seller = currentUserService.getCurrentUserProfile();
        Page<Listing> listings = listingRepository.findAllBySellerId(seller.getId(), pageable);
        return enrichSummaryPage(listings);
    }

    // ── Buyer / public operations ─────────────────────────────────────────────

    @Override
    public Page<ListingSummaryResponse> browseActiveListings(Pageable pageable) {
        return enrichSummaryPage(
                listingRepository.findAllByStatus(ListingStatus.ACTIVE, pageable));
    }

    @Override
    public Page<ListingSummaryResponse> searchListings(SearchListingsRequest filter, Pageable pageable) {
        return enrichSummaryPage(
                listingSearchRepository.search(filter, pageable));
    }

    @Override
    public ListingDetailsResponse getListingDetails(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        return buildDetails(listing);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Loads a listing by ID and asserts the current user is the seller.
     */
    private Listing requireOwnedListing(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));

        String currentKeycloakId = currentUserService.getCurrentKeycloakUserId();
        if (!listing.getSeller().getKeycloakUserId().equals(currentKeycloakId)) {
            throw new UnauthorizedException("You are not the seller of listing: " + listingId);
        }
        return listing;
    }

    /**
     * Maps a page of {@link Listing} entities to {@link ListingSummaryResponse} DTOs,
     * using a single batch query to load vehicle thumbnails (no N+1).
     */
    private Page<ListingSummaryResponse> enrichSummaryPage(Page<Listing> listings) {
        if (listings.isEmpty()) {
            return listings.map(listingMapper::toSummaryResponse);
        }

        List<Long> vehicleIds = listings.getContent().stream()
                .map(l -> l.getVehicle().getId())
                .collect(Collectors.toList());

        Map<Long, VehicleMedia> thumbnailMap = vehicleMediaRepository
                .findThumbnailsWithAttachmentByVehicleIds(vehicleIds)
                .stream()
                .collect(Collectors.toMap(
                        vm -> vm.getVehicle().getId(),
                        vm -> vm,
                        (a, b) -> a));

        return listings.map(listing -> {
            ListingSummaryResponse summary = listingMapper.toSummaryResponse(listing);

            VehicleMedia thumbnail = thumbnailMap.get(listing.getVehicle().getId());
            if (thumbnail != null) {
                summary.setPrimaryImageId(thumbnail.getAttachment().getId());
                summary.setPrimaryImageFileName(thumbnail.getAttachment().getFileName());
            }

            if (listing.getPublishedAt() != null) {
                summary.setDaysOnMarket(
                        ChronoUnit.DAYS.between(listing.getPublishedAt(), Instant.now()));
            }

            return summary;
        });
    }

    /**
     * Assembles a full {@link ListingDetailsResponse}, enriching it with vehicle
     * media (gallery + primary image) and the latest valuation snapshot.
     */
    private ListingDetailsResponse buildDetails(Listing listing) {
        ListingDetailsResponse response = listingMapper.toDetailsResponse(listing);

        Long vehicleId = listing.getVehicle().getId();

        // ── Vehicle media – single source of truth ─────────────────────────
        List<VehicleMedia> media = vehicleMediaRepository
                .findAllWithAttachmentByVehicleId(vehicleId);

        response.setGallery(listingMapper.toVehicleMediaResponses(media));

        if (!media.isEmpty()) {
            response.setPrimaryImage(listingMapper.toVehicleMediaResponse(media.get(0)));
        }

        // ── Days on market ─────────────────────────────────────────────────
        if (listing.getPublishedAt() != null) {
            response.setDaysOnMarket(
                    ChronoUnit.DAYS.between(listing.getPublishedAt(), Instant.now()));
        }

        // ── Valuation summary ──────────────────────────────────────────────
        vehicleValuationHistoryRepository
                .findFirstByVehicleIdOrderByValuationDateDesc(vehicleId)
                .ifPresent(v -> response.setValuationSummary(
                        listingMapper.toValuationSummaryResponse(v)));

        return response;
    }
}
