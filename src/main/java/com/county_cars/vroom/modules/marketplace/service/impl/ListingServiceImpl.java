package com.county_cars.vroom.modules.marketplace.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentVisibility;
import com.county_cars.vroom.modules.attachment.repository.AttachmentRepository;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.garage.repository.VehicleRepository;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.marketplace.dto.request.AddListingImagesRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateEnquiryRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.CreateListingRequest;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.dto.response.EnquiryResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingDetailsResponse;
import com.county_cars.vroom.modules.marketplace.dto.response.ListingSummaryResponse;
import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.marketplace.entity.ListingAttachment;
import com.county_cars.vroom.modules.marketplace.entity.ListingEnquiry;
import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import com.county_cars.vroom.modules.marketplace.mapper.ListingMapper;
import com.county_cars.vroom.modules.marketplace.repository.ListingAttachmentRepository;
import com.county_cars.vroom.modules.marketplace.repository.ListingEnquiryRepository;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingServiceImpl implements ListingService {

    private static final int MAX_IMAGES_PER_LISTING = 5;

    private final ListingRepository            listingRepository;
    private final ListingAttachmentRepository  listingAttachmentRepository;
    private final ListingEnquiryRepository     listingEnquiryRepository;
    private final ListingSearchRepository      listingSearchRepository;
    private final VehicleRepository            vehicleRepository;
    private final AttachmentRepository         attachmentRepository;
    private final ListingMapper                listingMapper;
    private final CurrentUserService           currentUserService;

    // ── Seller operations ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public ListingDetailsResponse createListing(CreateListingRequest request) {
        UserProfile seller = currentUserService.getCurrentUserProfile();

        Vehicle vehicle = vehicleRepository
                .findByIdAndOwnerKeycloakId(request.getVehicleId(), seller.getKeycloakUserId())
                .orElseThrow(() -> new NotFoundException(
                        "Vehicle not found or does not belong to the current user: " + request.getVehicleId()));

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
    public ListingDetailsResponse addListingImages(Long listingId, AddListingImagesRequest request) {
        Listing listing = requireOwnedListing(listingId);

        int existing = listingAttachmentRepository.countByListingId(listingId);
        int incoming = request.getAttachmentIds().size();

        if (existing + incoming > MAX_IMAGES_PER_LISTING) {
            throw new BadRequestException(
                    "Adding " + incoming + " image(s) would exceed the maximum of "
                    + MAX_IMAGES_PER_LISTING + " images per listing. Current count: " + existing);
        }

        int nextOrder = existing + 1;

        for (Long attachmentId : request.getAttachmentIds()) {
            if (listingAttachmentRepository.existsByListingIdAndAttachmentId(listingId, attachmentId)) {
                throw new BadRequestException("Attachment " + attachmentId + " is already linked to this listing");
            }

            Attachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));

            validateVehicleImage(attachment);

            ListingAttachment la = ListingAttachment.builder()
                    .listing(listing)
                    .attachment(attachment)
                    .displayOrder(nextOrder++)
                    .build();
            listingAttachmentRepository.save(la);

            // Set first image as primary
            if (listing.getPrimaryImage() == null) {
                listing.setPrimaryImage(attachment);
            }
        }

        // If primary image was just set for the first time, persist it
        Listing saved = listingRepository.save(listing);
        log.info("Added {} image(s) to listing {}", incoming, listingId);

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

    // ── Buyer / public operations ─────────────────────────────────────────────

    @Override
    public Page<ListingSummaryResponse> browseActiveListings(Pageable pageable) {
        return listingRepository
                .findAllByStatus(ListingStatus.ACTIVE, pageable)
                .map(listingMapper::toSummaryResponse);
    }

    @Override
    public Page<ListingSummaryResponse> searchListings(SearchListingsRequest filter, Pageable pageable) {
        return listingSearchRepository
                .search(filter, pageable)
                .map(listingMapper::toSummaryResponse);
    }

    @Override
    public ListingDetailsResponse getListingDetails(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        return buildDetails(listing);
    }

    @Override
    @Transactional
    public EnquiryResponse submitEnquiry(Long listingId, CreateEnquiryRequest request) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BadRequestException("Enquiries can only be sent on ACTIVE listings");
        }

        ListingEnquiry enquiry = ListingEnquiry.builder()
                .listing(listing)
                .message(request.getMessage())
                .build();

        ListingEnquiry saved = listingEnquiryRepository.save(enquiry);
        log.info("Enquiry submitted: enquiryId={}, listingId={}", saved.getId(), listingId);

        return listingMapper.toEnquiryResponse(saved);
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
     * Validates that an attachment is a PUBLIC image.
     * Category is inferred from the MIME type (no category field on Attachment).
     */
    private void validateVehicleImage(Attachment attachment) {
        if (!attachment.getContentType().startsWith("image/")) {
            throw new BadRequestException(
                    "Attachment " + attachment.getId() + " must be an image, "
                    + "but detected MIME type was: " + attachment.getContentType());
        }
        if (attachment.getVisibility() != AttachmentVisibility.PUBLIC) {
            throw new BadRequestException(
                    "Attachment " + attachment.getId() + " must have visibility PUBLIC, "
                    + "but was: " + attachment.getVisibility());
        }
    }

    /**
     * Assembles a full ListingDetailsResponse including ordered images.
     */
    private ListingDetailsResponse buildDetails(Listing listing) {
        ListingDetailsResponse response = listingMapper.toDetailsResponse(listing);

        List<ListingAttachment> attachments =
                listingAttachmentRepository.findAllByListingIdOrderByDisplayOrderAsc(listing.getId());

        response.setImages(listingMapper.toImageResponses(attachments));
        return response;
    }
}




