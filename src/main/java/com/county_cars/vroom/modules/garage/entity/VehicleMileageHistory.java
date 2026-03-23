package com.county_cars.vroom.modules.garage.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable mileage snapshot appended each time the vehicle's odometer is
 * recorded (MOT, service, user entry).  Supports the mileage-over-time graph.
 * No soft-delete – history records are never removed.
 */
@Entity
@Table(name = "vehicle_mileage_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleMileageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "mileage", nullable = false)
    private Long mileage;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    /** e.g. MOT, SERVICE, USER_ENTRY, IMPORT */
    @Column(name = "source", length = 50)
    private String source;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}

