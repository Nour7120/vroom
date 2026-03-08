package com.county_cars.vroom.modules.garage.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "vehicle_ownership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class VehicleOwnership extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserProfile owner;

    @Column(name = "ownership_start")
    private LocalDate ownershipStart;

    /** Null means this is the current owner */
    @Column(name = "ownership_end")
    private LocalDate ownershipEnd;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = Boolean.FALSE;
}

