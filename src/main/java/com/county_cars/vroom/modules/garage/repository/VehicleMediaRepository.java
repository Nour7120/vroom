package com.county_cars.vroom.modules.garage.repository;

import com.county_cars.vroom.modules.garage.entity.VehicleMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleMediaRepository extends JpaRepository<VehicleMedia, Long> {

    List<VehicleMedia> findAllByVehicleIdOrderByDisplayOrderAsc(Long vehicleId);

    List<VehicleMedia> findAllByVehicleId(Long vehicleId);

    Optional<VehicleMedia> findByIdAndVehicleId(Long id, Long vehicleId);

    Optional<VehicleMedia> findByVehicleIdAndDisplayOrder(Long vehicleId, Integer displayOrder);

    /** Fetch thumbnail (displayOrder=1) for multiple vehicles in a single query. */
    @Query("SELECT vm FROM VehicleMedia vm WHERE vm.vehicle.id IN :vehicleIds AND vm.displayOrder = 1")
    List<VehicleMedia> findThumbnailsByVehicleIds(@Param("vehicleIds") List<Long> vehicleIds);

    /**
     * Same as {@link #findThumbnailsByVehicleIds} but eagerly fetches the
     * {@code attachment} association to prevent N+1 when mapping to DTOs.
     */
    @Query("SELECT vm FROM VehicleMedia vm JOIN FETCH vm.attachment WHERE vm.vehicle.id IN :vehicleIds AND vm.displayOrder = 1")
    List<VehicleMedia> findThumbnailsWithAttachmentByVehicleIds(@Param("vehicleIds") List<Long> vehicleIds);

    /**
     * Loads the full media set for a single vehicle, with the attachment eagerly
     * fetched to prevent N+1 when building the listing gallery.
     */
    @Query("SELECT vm FROM VehicleMedia vm JOIN FETCH vm.attachment WHERE vm.vehicle.id = :vehicleId ORDER BY vm.displayOrder ASC")
    List<VehicleMedia> findAllWithAttachmentByVehicleId(@Param("vehicleId") Long vehicleId);

    /** Count per-vehicle media for the garage list enrichment. */
    @Query("SELECT vm.vehicle.id, COUNT(vm) FROM VehicleMedia vm WHERE vm.vehicle.id IN :vehicleIds GROUP BY vm.vehicle.id")
    List<Object[]> countByVehicleIds(@Param("vehicleIds") List<Long> vehicleIds);

    /** Image count (MIME starts with image/) for the max-per-vehicle guard. */
    @Query("SELECT COUNT(vm) FROM VehicleMedia vm WHERE vm.vehicle.id = :vehicleId AND vm.attachment.contentType LIKE 'image/%'")
    long countImagesByVehicleId(@Param("vehicleId") Long vehicleId);

    /** Video count (MIME starts with video/) for the max-per-vehicle guard. */
    @Query("SELECT COUNT(vm) FROM VehicleMedia vm WHERE vm.vehicle.id = :vehicleId AND vm.attachment.contentType LIKE 'video/%'")
    long countVideosByVehicleId(@Param("vehicleId") Long vehicleId);
}
