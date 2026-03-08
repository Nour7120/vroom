package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleMileageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleMileageHistoryRepository extends JpaRepository<VehicleMileageHistory, Long> {

    List<VehicleMileageHistory> findAllByVehicleIdOrderByRecordedDateDesc(Long vehicleId);
}

