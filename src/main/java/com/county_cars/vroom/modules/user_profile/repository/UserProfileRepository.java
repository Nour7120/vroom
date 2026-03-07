package com.county_cars.vroom.modules.user_profile.repository;

import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByKeycloakUserId(String keycloakUserId);
    Optional<UserProfile> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByKeycloakUserId(String keycloakUserId);
    boolean existsByDisplayName(String displayName);
    Page<UserProfile> findAllByIsDeletedFalse(Pageable pageable);
}

