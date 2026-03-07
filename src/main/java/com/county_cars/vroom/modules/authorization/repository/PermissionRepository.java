package com.county_cars.vroom.modules.authorization.repository;

import com.county_cars.vroom.modules.authorization.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByNameAndIsDeletedFalse(String name);
    boolean existsByName(String name);
}

