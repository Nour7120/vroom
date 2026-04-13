package com.county_cars.vroom.modules.marketplace.controller;

import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.UpdateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingDetailsResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingSummaryResponse;
import com.county_cars.vroom.modules.marketplace.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
@Tag(name = "Marketplace", description = "Marketplace listing management")
public class ListingController {

    private final ListingService listingService;

    // ── Seller: create listing ────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new listing (DRAFT) from an owned vehicle",
               description = "The vehicle must belong to the authenticated seller. " +
                             "An existing DRAFT or ACTIVE listing for the same vehicle is rejected. " +
                             "Media is automatically sourced from vehicle.media.")
    public ResponseEntity<ListingDetailsResponse> createListing(
            @Valid @RequestBody CreateListingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.createListing(request));
    }

    // ── Seller: update listing ────────────────────────────────────────────────

    @PatchMapping("/{listingId}")
    @Operation(summary = "Partially update a listing (price, description, location, featured)",
               description = "Only non-null fields are applied. Allowed for DRAFT and ACTIVE listings only.")
    public ResponseEntity<ListingDetailsResponse> update(
            @PathVariable Long listingId,
            @Valid @RequestBody UpdateListingRequest request) {
        return ResponseEntity.ok(listingService.updateListing(listingId, request));
    }

    // ── Seller: publish ───────────────────────────────────────────────────────

    @PostMapping("/{listingId}/publish")
    @Operation(summary = "Publish a DRAFT listing (transitions to ACTIVE)")
    public ResponseEntity<ListingDetailsResponse> publish(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.publishListing(listingId));
    }

    // ── Seller: mark sold ─────────────────────────────────────────────────────

    @PostMapping("/{listingId}/sold")
    @Operation(summary = "Mark an ACTIVE listing as SOLD")
    public ResponseEntity<ListingDetailsResponse> markSold(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.markListingSold(listingId));
    }

    // ── Seller: withdraw listing ──────────────────────────────────────────────

    @PostMapping("/{listingId}/withdraw")
    @Operation(summary = "Withdraw an ACTIVE or DRAFT listing (seller-initiated removal)")
    public ResponseEntity<ListingDetailsResponse> withdraw(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.withdrawListing(listingId));
    }

    // ── Seller: my listings dashboard ────────────────────────────────────────

    @GetMapping("/my-listings")
    @Operation(summary = "Get all listings owned by the authenticated seller",
               description = "Returns all statuses (DRAFT, ACTIVE, SOLD, WITHDRAWN, EXPIRED), paginated.")
    public ResponseEntity<Page<ListingSummaryResponse>> myListings(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(listingService.getMyListings(pageable));
    }

    // ── Buyer: browse ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Browse active listings (paginated)",
               description = "Primary image sourced from vehicle.media.")
    public ResponseEntity<Page<ListingSummaryResponse>> browse(
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(listingService.browseActiveListings(pageable));
    }

    // ── Buyer: search with filters ────────────────────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Search active listings with dynamic filters",
               description = "Supports filtering by make, model, year, price, mileage, " +
                             "fuelType, transmission, colour, location. " +
                             "Sort by: price, publishedAt, yearOfManufacture, currentMileage.")
    public ResponseEntity<Page<ListingSummaryResponse>> search(
            @Valid SearchListingsRequest filter,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(listingService.searchListings(filter, pageable));
    }

    // ── Buyer: listing details ────────────────────────────────────────────────

    @GetMapping("/{listingId}")
    @Operation(summary = "Get full listing details",
               description = "Returns vehicle info, full media gallery (vehicle.media), " +
                             "valuation summary, seller info and days on market.")
    public ResponseEntity<ListingDetailsResponse> getDetails(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.getListingDetails(listingId));
    }
}
