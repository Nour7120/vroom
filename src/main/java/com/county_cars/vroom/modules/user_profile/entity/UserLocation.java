package com.county_cars.vroom.modules.user_profile.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "user_location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class UserLocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "full_address")
    private String fullAddress;

    @Column(name = "appartment_no", length = 64)
    private String apartmentNo;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;
}
