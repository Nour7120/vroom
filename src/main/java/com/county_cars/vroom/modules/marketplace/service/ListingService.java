package com.county_cars.vroom.modules.marketplace.service;

import com.county_cars.vroom.modules.marketplace.dto.request.AddListingImagesRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateEnquiryRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.EnquiryResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingDetailsResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingService {

    // ── Seller operations ─────────────────────────────────────────────────────

    ListingDetailsResponse createListing(CreateListingRequest request);

    ListingDetailsResponse addListingImages(Long listingId, AddListingImagesRequest request);

    ListingDetailsResponse publishListing(Long listingId);

    ListingDetailsResponse markListingSold(Long listingId);

    // ── Buyer / public operations ─────────────────────────────────────────────

    Page<ListingSummaryResponse> browseActiveListings(Pageable pageable);

    Page<ListingSummaryResponse> searchListings(SearchListingsRequest filter, Pageable pageable);

    ListingDetailsResponse getListingDetails(Long listingId);

    EnquiryResponse submitEnquiry(Long listingId, CreateEnquiryRequest request);
}

