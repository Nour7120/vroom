package com.county_cars.vroom.modules.garage.mapper;

import com.county_cars.vroom.modules.garage.dto.request.CreateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.request.UpdateVehicleRequest;
import com.county_cars.vroom.modules.garage.dto.response.VehicleResponse;
import com.county_cars.vroom.modules.garage.entity.Vehicle;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VehicleMapper {

    /**
     * Maps a create request to a new Vehicle entity.
     * id, audit fields, and ownerKeycloakId are set by the service layer.
     */
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "ownerKeycloakId", ignore = true)
    Vehicle toEntity(CreateVehicleRequest request);

    /** Maps a Vehicle entity to the public response DTO. */
    VehicleResponse toResponse(Vehicle vehicle);

    List<VehicleResponse> toResponseList(List<Vehicle> vehicles);

    /**
     * Applies non-null fields from an update request onto an existing entity.
     * Null fields in the request are ignored so callers only need to send changed fields.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id",                 ignore = true)
    @Mapping(target = "registrationNumber", ignore = true)
    @Mapping(target = "vin",                ignore = true)
    @Mapping(target = "ownerKeycloakId",    ignore = true)
    void updateEntity(UpdateVehicleRequest request, @MappingTarget Vehicle vehicle);
}
