package com.county_cars.vroom.modules.garage.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "vehicle_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class VehicleMedia extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    /** 1-based ordering. Display order 1 = primary thumbnail. */
    @Column(name = "display_order")
    private Integer displayOrder;
}
