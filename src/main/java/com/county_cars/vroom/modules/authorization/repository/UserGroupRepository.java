package com.county_cars.vroom.modules.authorization.repository;

import com.county_cars.vroom.modules.authorization.entity.Group;
import com.county_cars.vroom.modules.authorization.entity.UserGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {
    Page<UserGroup> findAllByUserProfileIdAndIsDeletedFalse(Long userProfileId, Pageable pageable);
    Page<UserGroup> findAllByGroupIdAndIsDeletedFalse(Long groupId, Pageable pageable);
    Optional<UserGroup> findByUserProfileIdAndGroupIdAndIsDeletedFalse(Long userProfileId, Long groupId);
    boolean existsByUserProfileIdAndGroupIdAndIsDeletedFalse(Long userProfileId, Long groupId);
//
//    /**
//     * Loads all active groups (with their permissions eagerly) for the user identified by email.
//     * Used during JWT authentication to build GrantedAuthority list.
//     */
//    @Query("""
//            SELECT DISTINCT ug.group FROM UserGroup ug
//            JOIN FETCH ug.group g
//            JOIN FETCH g.permissions p
//            WHERE ug.userProfile.email = :email
//              AND ug.isDeleted = false
//              AND g.isDeleted = false
//              AND p.isDeleted = false
//            """)
//    List<Group> findGroupsWithPermissionsByEmail(@Param("email") String email);
}

