package com.county_cars.vroom.modules.garage.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per valuation snapshot.  Supports the valuation-history chart and
 * the Valuation Engine.  No soft-delete – valuation history is immutable.
 */
@Entity
@Table(name = "vehicle_valuation_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleValuationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "dealer_retail_value", precision = 15, scale = 2)
    private BigDecimal dealerRetailValue;

    @Column(name = "trade_in_value", precision = 15, scale = 2)
    private BigDecimal tradeInValue;

    @Column(name = "private_sale_value", precision = 15, scale = 2)
    private BigDecimal privateSaleValue;

    @Column(name = "auction_value", precision = 15, scale = 2)
    private BigDecimal auctionValue;

    @Column(name = "average_market_value", precision = 15, scale = 2)
    private BigDecimal averageMarketValue;

    /** HIGH, MEDIUM, LOW */
    @Column(name = "valuation_confidence", length = 20)
    private String valuationConfidence;

    @Column(name = "valuation_date")
    private LocalDateTime valuationDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}

