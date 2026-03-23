package com.county_cars.vroom.modules.garage.entity;

import com.county_cars.vroom.modules.attachment.entity.Attachment;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @Column(name = "display_order")
    private Integer displayOrder;
}

