package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleDocument;
import com.county_cars.vroom.modules.garage.entity.VehicleDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    List<VehicleDocument> findAllByVehicleId(Long vehicleId);

    List<VehicleDocument> findAllByVehicleIdAndDocumentType(Long vehicleId, VehicleDocumentType documentType);
}

