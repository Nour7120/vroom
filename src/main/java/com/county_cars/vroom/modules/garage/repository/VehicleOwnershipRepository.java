package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleOwnershipRepository extends JpaRepository<VehicleOwnership, Long> {

    List<VehicleOwnership> findAllByVehicleIdOrderByOwnershipStartDesc(Long vehicleId);

    Optional<VehicleOwnership> findByVehicleIdAndIsCurrentTrue(Long vehicleId);

    List<VehicleOwnership> findAllByOwnerIdOrderByOwnershipStartDesc(Long ownerId);
}

