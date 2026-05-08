package com.county_cars.vroom.modules.notification.repository;

import com.county_cars.vroom.modules.notification.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    /** All active registered devices for a user — used during push delivery. */
    List<UserDevice> findByUserIdAndActiveTrue(Long userId);

    /** All devices for a user including inactive — used for management endpoints. */
    List<UserDevice> findByUserId(Long userId);

    /**
     * Look up by FCM token — used for upsert logic.
     * A token may change ownership (device sold), so we track it by token string.
     */
    Optional<UserDevice> findByToken(String token);

    /** Ownership check: find by id AND user (prevents cross-user manipulation). */
    Optional<UserDevice> findByIdAndUserId(Long id, Long userId);
}

