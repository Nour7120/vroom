package com.county_cars.vroom.modules.authorization.repository;

import com.county_cars.vroom.modules.authorization.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByNameAndIsDeletedFalse(String name);
    boolean existsByName(String name);
}

