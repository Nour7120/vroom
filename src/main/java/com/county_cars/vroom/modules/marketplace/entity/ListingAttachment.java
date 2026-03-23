package com.county_cars.vroom.modules.marketplace.entity;

import com.county_cars.vroom.modules.attachment.entity.Attachment;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "listing_attachment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

