package com.county_cars.vroom.modules.marketplace.repository;

import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

    // @SQLRestriction("is_deleted = false") on the entity already filters soft-deleted rows.
    // Spring Data derived queries therefore only need to filter on business columns.

    Page<Listing> findAllByStatus(ListingStatus status, Pageable pageable);

    Optional<Listing> findById(Long id);

    boolean existsByIdAndSellerKeycloakUserId(Long id, String keycloakUserId);
}


