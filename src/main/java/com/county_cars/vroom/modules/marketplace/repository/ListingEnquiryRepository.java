package com.county_cars.vroom.modules.marketplace.repository;

import com.county_cars.vroom.modules.marketplace.entity.ListingEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingEnquiryRepository extends JpaRepository<ListingEnquiry, Long> {
}

