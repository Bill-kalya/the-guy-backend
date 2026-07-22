package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_price_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BookingPriceSnapshot extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(nullable = false)
    private String serviceCategory;

    @Column(nullable = false)
    private Double servicePrice;

    @Column(nullable = false)
    private Double platformFee;

    @Column(nullable = false)
    private Double taxAmount;

    @Column(nullable = false)
    private Double discountAmount;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;
}
