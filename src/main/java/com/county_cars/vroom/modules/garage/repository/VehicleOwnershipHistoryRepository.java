package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleOwnershipHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleOwnershipHistoryRepository extends JpaRepository<VehicleOwnershipHistory, Long> {

    List<VehicleOwnershipHistory> findAllByVehicleIdOrderByOwnershipStartDesc(Long vehicleId);
}

