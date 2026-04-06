package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.GarageVehicle;
import com.county_cars.vroom.modules.garage.entity.GarageVehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GarageVehicleRepository extends JpaRepository<GarageVehicle, Long> {

    List<GarageVehicle> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<GarageVehicle> findByUserIdAndVehicleId(Long userId, Long vehicleId);

    boolean existsByUserIdAndVehicleId(Long userId, Long vehicleId);

    List<GarageVehicle> findAllByUserIdAndCategoryOrderByCreatedAtDesc(Long userId, GarageVehicleCategory category);

    /** Used during vehicle cascade-delete to remove the entry from every user's garage. */
    List<GarageVehicle> findAllByVehicleId(Long vehicleId);
}

