package com.county_cars.vroom.modules.user_profile.repository;

import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByKeycloakUserId(String keycloakUserId);
    Optional<UserProfile> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByKeycloakUserId(String keycloakUserId);
    boolean existsByDisplayName(String displayName);
    Page<UserProfile> findAllByIsDeletedFalse(Pageable pageable);

    /**
     * Cursor-based pagination for the notification fan-out worker.
     *
     * <p>Selects the next batch of active user IDs whose {@code id > cursorId},
     * ordered ascending so the cursor always moves forward.
     * {@code @SQLRestriction("is_deleted = false")} on the entity ensures only
     * active users are returned without an extra filter clause here.</p>
     */
    @Query("SELECT u.id FROM UserProfile u WHERE u.id > :cursorId ORDER BY u.id ASC")
    List<Long> findActiveIdsAfterCursor(@Param("cursorId") Long cursorId, Pageable pageable);
}

