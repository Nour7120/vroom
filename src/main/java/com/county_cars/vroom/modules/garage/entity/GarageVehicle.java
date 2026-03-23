package com.county_cars.vroom.modules.garage.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "garage_vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class GarageVehicle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private GarageVehicleCategory category;

    @Column(name = "notes", length = 1000)
    private String notes;
}

