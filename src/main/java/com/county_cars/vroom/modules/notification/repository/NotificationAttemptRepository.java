package com.county_cars.vroom.modules.notification.repository;

import com.county_cars.vroom.modules.notification.entity.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

    /** Full attempt history for a notification — used by admin diagnostics. */
    List<NotificationAttempt> findByNotificationIdOrderByAttemptNumberAsc(Long notificationId);

    /** How many attempts have been recorded so far for a notification. */
    int countByNotificationId(Long notificationId);
}

