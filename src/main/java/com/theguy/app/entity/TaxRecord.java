package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tax_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaxRecord extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false)
    private String taxType;

    @Column(nullable = false)
    private Double taxRate;

    @Column(nullable = false)
    private Double taxAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";
}
