package com.county_cars.vroom.modules.user_profile.repository;

import com.county_cars.vroom.modules.user_profile.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {
    List<UserLocation> findAllByUserProfileIdAndIsDeletedFalse(Long userProfileId);
    Optional<UserLocation> findByIdAndUserProfileIdAndIsDeletedFalse(Long id, Long userProfileId);
    boolean existsByUserProfileIdAndIsPrimaryTrue(Long userProfileId);
}

