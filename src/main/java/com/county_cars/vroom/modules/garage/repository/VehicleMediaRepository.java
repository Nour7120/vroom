package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleMediaRepository extends JpaRepository<VehicleMedia, Long> {

    List<VehicleMedia> findAllByVehicleIdOrderByDisplayOrderAsc(Long vehicleId);
}

