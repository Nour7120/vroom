package com.county_cars.vroom.modules.user_profile.service;

import com.county_cars.vroom.modules.user_profile.dto.request.CreateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UpdateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UserLocationRequest;
import com.county_cars.vroom.modules.user_profile.dto.response.UserLocationResponse;
import com.county_cars.vroom.modules.user_profile.dto.response.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserProfileService {
    UserProfileResponse createUserProfile(CreateUserProfileRequest request);
    UserProfileResponse getUserProfileById(Long id);
    UserProfileResponse getMyProfile();
    Page<UserProfileResponse> getAllUserProfiles(Pageable pageable);
    UserProfileResponse updateUserProfile(Long id, UpdateUserProfileRequest request);
    void deleteUserProfile(Long id);

    UserLocationResponse addLocation(Long userProfileId, UserLocationRequest request);
    List<UserLocationResponse> getLocations(Long userProfileId);
    UserLocationResponse updateLocation(Long userProfileId, Long locationId, UserLocationRequest request);
    void deleteLocation(Long userProfileId, Long locationId);
}

