package com.county_cars.vroom.modules.garage.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class Vehicle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Column(name = "registration_number", length = 20)
    private String registrationNumber;

    @Column(name = "vin", length = 50)
    private String vin;

    // ── Classification ────────────────────────────────────────────────────────

    @Column(name = "make", nullable = false, length = 100)
    private String make;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "variant", length = 100)
    private String variant;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    // ── Technical spec ────────────────────────────────────────────────────────

    @Column(name = "fuel_type", length = 50)
    private String fuelType;

    @Column(name = "transmission", length = 50)
    private String transmission;

    @Column(name = "engine_capacity")
    private Integer engineCapacity;

    @Column(name = "colour", length = 50)
    private String colour;

    @Column(name = "number_of_doors")
    private Integer numberOfDoors;

    @Column(name = "body_type", length = 50)
    private String bodyType;

    @Column(name = "co2_emissions")
    private Integer co2Emissions;

    // ── Current snapshot ──────────────────────────────────────────────────────
    // These are CURRENT values only. Historical data lives in dedicated history
    // tables (vehicle_mileage_history, vehicle_mot_history, etc.).

    @Column(name = "current_mileage")
    private Long currentMileage;

    @Column(name = "first_registration_date")
    private LocalDate firstRegistrationDate;

    @Column(name = "previous_owners")
    private Integer previousOwners;

    @Column(name = "mot_expiry_date")
    private LocalDate motExpiryDate;

    @Column(name = "tax_expiry_date")
    private LocalDate taxExpiryDate;

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Column(name = "owner_keycloak_id", nullable = false, length = 36)
    private String ownerKeycloakId;
}
