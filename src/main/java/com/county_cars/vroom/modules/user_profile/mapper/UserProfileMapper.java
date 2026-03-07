package com.county_cars.vroom.modules.user_profile.mapper;

import com.county_cars.vroom.modules.user_profile.dto.request.CreateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UpdateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UserLocationRequest;
import com.county_cars.vroom.modules.user_profile.dto.response.UserLocationResponse;
import com.county_cars.vroom.modules.user_profile.dto.response.UserProfileResponse;
import com.county_cars.vroom.modules.user_profile.entity.UserLocation;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfileMapper {

    UserProfile toEntity(CreateUserProfileRequest request);

    UserProfileResponse toResponse(UserProfile entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(UpdateUserProfileRequest request, @MappingTarget UserProfile entity);

    @Mapping(target = "userProfileId", source = "userProfile.id")
    UserLocationResponse toLocationResponse(UserLocation entity);

    @Mapping(target = "userProfile", ignore = true)
    UserLocation toLocationEntity(UserLocationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userProfile", ignore = true)
    void updateLocationFromRequest(UserLocationRequest request, @MappingTarget UserLocation entity);
}

