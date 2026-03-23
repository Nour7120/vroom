package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleMotHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleMotHistoryRepository extends JpaRepository<VehicleMotHistory, Long> {

    List<VehicleMotHistory> findAllByVehicleIdOrderByTestDateDesc(Long vehicleId);
}

