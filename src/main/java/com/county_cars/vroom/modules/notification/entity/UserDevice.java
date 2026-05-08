package com.county_cars.vroom.modules.notification.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * FCM device token for a specific user and platform.
 *
 * <p>A user may have multiple devices (phone + tablet + web).
 * The unique index on {@code token} (where not deleted) prevents duplicates
 * from app reinstalls — the upsert logic in the service updates the owner if the token
 * is already registered to a different user (device transfer).</p>
 */
@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class UserDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** FCM registration token. Treated as opaque; may be rotated by FCM. */
    @Column(nullable = false, length = 1024)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DevicePlatform platform = DevicePlatform.ANDROID;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Updated whenever the device checks in (heartbeat / app open). */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}

