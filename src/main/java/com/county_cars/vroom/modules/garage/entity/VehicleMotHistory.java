package com.county_cars.vroom.modules.garage.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per MOT test (pass or fail).  Supports the MOT-history timeline
 * shown in the Vehicle Passport.  No soft-delete – history is immutable.
 */
@Entity
@Table(name = "vehicle_mot_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleMotHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** PASS or FAIL */
    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "advisory", columnDefinition = "TEXT")
    private String advisory;

    @Column(name = "failure_items", columnDefinition = "TEXT")
    private String failureItems;

    @Column(name = "mileage")
    private Long mileage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}

