package com.county_cars.vroom.modules.marketplace.controller;

import com.county_cars.vroom.modules.marketplace.dto.request.AddListingImagesRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateEnquiryRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.EnquiryResponse;
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
@Tag(name = "Marketplace", description = "Marketplace listing, image management, and buyer enquiries")
public class ListingController {

    private final ListingService listingService;

    // ── Seller: create listing ────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new listing (DRAFT) from an owned vehicle")
    public ResponseEntity<ListingDetailsResponse> createListing(
            @Valid @RequestBody CreateListingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.createListing(request));
    }

    // ── Seller: add images ────────────────────────────────────────────────────

    @PostMapping("/{listingId}/images")
    @Operation(summary = "Add images to a listing (max 5 total, VEHICLE_IMAGE + PUBLIC)")
    public ResponseEntity<ListingDetailsResponse> addImages(
            @PathVariable Long listingId,
            @Valid @RequestBody AddListingImagesRequest request) {
        return ResponseEntity.ok(listingService.addListingImages(listingId, request));
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

    // ── Buyer: browse ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Browse active listings (paginated)")
    public ResponseEntity<Page<ListingSummaryResponse>> browse(
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(listingService.browseActiveListings(pageable));
    }

    // ── Buyer: listing details ────────────────────────────────────────────────

    @GetMapping("/{listingId}")
    @Operation(summary = "Get full listing details including vehicle info, seller, and images")
    public ResponseEntity<ListingDetailsResponse> getDetails(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.getListingDetails(listingId));
    }

    // ── Buyer: submit enquiry ─────────────────────────────────────────────────

    @PostMapping("/{listingId}/enquiries")
    @Operation(summary = "Submit a buyer enquiry on an active listing")
    public ResponseEntity<EnquiryResponse> submitEnquiry(
            @PathVariable Long listingId,
            @Valid @RequestBody CreateEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.submitEnquiry(listingId, request));
    }
}

