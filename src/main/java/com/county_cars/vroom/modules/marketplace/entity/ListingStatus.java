package com.county_cars.vroom.modules.marketplace.entity;

public enum ListingStatus {
    DRAFT,
    ACTIVE,
    SOLD,
    /** Seller voluntarily removed the listing before it was sold. */
    WITHDRAWN,
    EXPIRED
}

