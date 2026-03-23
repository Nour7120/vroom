package com.county_cars.vroom.modules.marketplace.repository;

import com.county_cars.vroom.modules.garage.entity.Vehicle;
import com.county_cars.vroom.modules.marketplace.dto.request.SearchListingsRequest;
import com.county_cars.vroom.modules.marketplace.entity.Listing;
import com.county_cars.vroom.modules.marketplace.entity.ListingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic marketplace search using JPA Criteria API.
 *
 * <p>Only joins {@code listing} → {@code vehicle}.
 * No history tables are touched.
 * All active vehicle and listing indexes are reachable by the generated SQL.</p>
 */
@Repository
@RequiredArgsConstructor
public class ListingSearchRepository {

    private static final String FIELD_PRICE            = "price";
    private static final String FIELD_PUBLISHED_AT     = "publishedAt";
    private static final String FIELD_YEAR             = "yearOfManufacture";
    private static final String FIELD_MILEAGE          = "currentMileage";

    private final EntityManager em;

    // ── Public API ────────────────────────────────────────────────────────────

    public Page<Listing> search(SearchListingsRequest filter, Pageable pageable) {
        List<Listing> content = executeQuery(filter, pageable);
        long total            = executeCountQuery(filter);
        return new PageImpl<>(content, pageable, total);
    }

    // ── Query builders ────────────────────────────────────────────────────────

    private List<Listing> executeQuery(SearchListingsRequest filter, Pageable pageable) {
        CriteriaBuilder  cb    = em.getCriteriaBuilder();
        CriteriaQuery<Listing> cq = cb.createQuery(Listing.class);
        Root<Listing>    root  = cq.from(Listing.class);
        Join<Listing, Vehicle> vehicle = root.join("vehicle", JoinType.INNER);

        cq.select(root)
          .where(buildPredicates(filter, cb, root, vehicle).toArray(new Predicate[0]))
          .orderBy(buildOrderBy(pageable.getSort(), cb, root, vehicle));

        TypedQuery<Listing> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return query.getResultList();
    }

    private long executeCountQuery(SearchListingsRequest filter) {
        CriteriaBuilder       cb   = em.getCriteriaBuilder();
        CriteriaQuery<Long>   cq   = cb.createQuery(Long.class);
        Root<Listing>         root = cq.from(Listing.class);
        Join<Listing, Vehicle> vehicle = root.join("vehicle", JoinType.INNER);

        cq.select(cb.count(root))
          .where(buildPredicates(filter, cb, root, vehicle).toArray(new Predicate[0]));

        return em.createQuery(cq).getSingleResult();
    }

    // ── Predicate builder ─────────────────────────────────────────────────────

    private List<Predicate> buildPredicates(
            SearchListingsRequest filter,
            CriteriaBuilder cb,
            Root<Listing> listing,
            Join<Listing, Vehicle> vehicle) {

        List<Predicate> predicates = new ArrayList<>();

        // Always restrict to ACTIVE listings only
        predicates.add(cb.equal(listing.get("status"), ListingStatus.ACTIVE));

        // ── Vehicle filters ───────────────────────────────────────────────────
        if (StringUtils.hasText(filter.getMake())) {
            predicates.add(cb.equal(
                    cb.lower(vehicle.get("make")),
                    filter.getMake().toLowerCase()));
        }
        if (StringUtils.hasText(filter.getModel())) {
            predicates.add(cb.equal(
                    cb.lower(vehicle.get("model")),
                    filter.getModel().toLowerCase()));
        }
        if (filter.getYearMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    vehicle.get("yearOfManufacture"), filter.getYearMin()));
        }
        if (filter.getYearMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    vehicle.get("yearOfManufacture"), filter.getYearMax()));
        }
        if (filter.getMileageMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    vehicle.get("currentMileage"), filter.getMileageMin()));
        }
        if (filter.getMileageMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    vehicle.get("currentMileage"), filter.getMileageMax()));
        }
        if (StringUtils.hasText(filter.getFuelType())) {
            predicates.add(cb.equal(
                    cb.lower(vehicle.get("fuelType")),
                    filter.getFuelType().toLowerCase()));
        }
        if (StringUtils.hasText(filter.getTransmission())) {
            predicates.add(cb.equal(
                    cb.lower(vehicle.get("transmission")),
                    filter.getTransmission().toLowerCase()));
        }
        if (StringUtils.hasText(filter.getColour())) {
            predicates.add(cb.equal(
                    cb.lower(vehicle.get("colour")),
                    filter.getColour().toLowerCase()));
        }

        // ── Listing filters ───────────────────────────────────────────────────
        if (filter.getPriceMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    listing.get("price"), filter.getPriceMin()));
        }
        if (filter.getPriceMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    listing.get("price"), filter.getPriceMax()));
        }
        if (StringUtils.hasText(filter.getLocation())) {
            predicates.add(cb.like(
                    cb.lower(listing.get("location")),
                    "%" + filter.getLocation().toLowerCase() + "%"));
        }

        return predicates;
    }

    // ── Order-by builder ──────────────────────────────────────────────────────

    private List<Order> buildOrderBy(
            Sort sort,
            CriteriaBuilder cb,
            Root<Listing> listing,
            Join<Listing, Vehicle> vehicle) {

        List<Order> orders = new ArrayList<>();

        for (Sort.Order o : sort) {
            Expression<?> expr = resolveExpression(o.getProperty(), listing, vehicle);
            if (expr != null) {
                orders.add(o.isAscending() ? cb.asc(expr) : cb.desc(expr));
            }
        }

        // Default: newest first
        if (orders.isEmpty()) {
            orders.add(cb.desc(listing.get(FIELD_PUBLISHED_AT)));
        }

        return orders;
    }

    /**
     * Maps a public sort property name to the correct JPA path expression.
     * Returns {@code null} for unknown properties (silently ignored).
     */
    private Expression<?> resolveExpression(
            String property,
            Root<Listing> listing,
            Join<Listing, Vehicle> vehicle) {

        return switch (property) {
            case FIELD_PRICE        -> listing.get(FIELD_PRICE);
            case FIELD_PUBLISHED_AT -> listing.get(FIELD_PUBLISHED_AT);
            case FIELD_YEAR         -> vehicle.get(FIELD_YEAR);
            case FIELD_MILEAGE      -> vehicle.get(FIELD_MILEAGE);
            default                 -> null;
        };
    }
}

