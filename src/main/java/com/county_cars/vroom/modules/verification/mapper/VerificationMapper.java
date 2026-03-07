package com.county_cars.vroom.modules.verification.mapper;

import com.county_cars.vroom.modules.verification.dto.request.CreateVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.response.VerificationResponse;
import com.county_cars.vroom.modules.verification.entity.VerificationRequest;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VerificationMapper {

    @Mapping(target = "userProfile", ignore = true)
    VerificationRequest toEntity(CreateVerificationRequest request);

    @Mapping(target = "userProfileId", source = "userProfile.id")
    VerificationResponse toResponse(VerificationRequest entity);
}

