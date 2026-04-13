package com.county_cars.vroom.modules.marketplace.service;

import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.UpdateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingDetailsResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingService {

    // ── Seller operations ─────────────────────────────────────────────────────

    /** Create a new DRAFT listing for an owned vehicle. */
    ListingDetailsResponse createListing(CreateListingRequest request);

    /** Partial update of a listing (price, description, location, featured). */
    ListingDetailsResponse updateListing(Long listingId, UpdateListingRequest request);

    /** Transition a DRAFT listing to ACTIVE and set publishedAt. */
    ListingDetailsResponse publishListing(Long listingId);

    /** Transition an ACTIVE listing to SOLD. */
    ListingDetailsResponse markListingSold(Long listingId);

    /** Transition an ACTIVE or DRAFT listing to WITHDRAWN (seller-initiated removal). */
    ListingDetailsResponse withdrawListing(Long listingId);

    /** Retrieve all listings owned by the currently-authenticated seller. */
    Page<ListingSummaryResponse> getMyListings(Pageable pageable);

    // ── Buyer / public operations ─────────────────────────────────────────────

    Page<ListingSummaryResponse> browseActiveListings(Pageable pageable);

    Page<ListingSummaryResponse> searchListings(SearchListingsRequest filter, Pageable pageable);

    ListingDetailsResponse getListingDetails(Long listingId);
}
