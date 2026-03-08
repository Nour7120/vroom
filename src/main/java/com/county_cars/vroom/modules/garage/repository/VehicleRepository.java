package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByIdAndOwnerKeycloakId(Long id, String ownerKeycloakId);

    boolean existsByIdAndOwnerKeycloakId(Long id, String ownerKeycloakId);

    // ── Uniqueness guards ─────────────────────────────────────────────────────

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByVin(String vin);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    Optional<Vehicle> findByVin(String vin);
}
