package com.county_cars.vroom.modules.garage.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per ownership period.  A null {@code ownershipEnd} indicates the
 * current owner.  Supports the ownership-timeline view in the Vehicle Passport.
 * No soft-delete – history is immutable.
 */
@Entity
@Table(name = "vehicle_ownership_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleOwnershipHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** References user_profile.id – nullable for historical owners before platform join */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "ownership_start")
    private LocalDate ownershipStart;

    /** Null means current owner */
    @Column(name = "ownership_end")
    private LocalDate ownershipEnd;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}

