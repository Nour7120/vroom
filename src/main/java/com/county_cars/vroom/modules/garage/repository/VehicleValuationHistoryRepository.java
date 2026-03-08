package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleValuationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleValuationHistoryRepository extends JpaRepository<VehicleValuationHistory, Long> {

    List<VehicleValuationHistory> findAllByVehicleIdOrderByValuationDateDesc(Long vehicleId);

    /** Returns the most recent valuation for a vehicle */
    Optional<VehicleValuationHistory> findFirstByVehicleIdOrderByValuationDateDesc(Long vehicleId);
}

