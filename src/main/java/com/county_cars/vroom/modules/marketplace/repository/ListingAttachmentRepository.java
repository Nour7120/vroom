package com.county_cars.vroom.modules.marketplace.repository;

import com.county_cars.vroom.modules.marketplace.entity.ListingAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingAttachmentRepository extends JpaRepository<ListingAttachment, Long> {

    List<ListingAttachment> findAllByListingIdOrderByDisplayOrderAsc(Long listingId);

    int countByListingId(Long listingId);

    boolean existsByListingIdAndAttachmentId(Long listingId, Long attachmentId);
}

