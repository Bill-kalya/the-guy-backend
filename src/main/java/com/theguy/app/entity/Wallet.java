package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Wallet extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    @Column(nullable = false)
    @Builder.Default
    private Double pendingBalance = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Double availableBalance = 0.0;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Version
    private Integer version;
}
