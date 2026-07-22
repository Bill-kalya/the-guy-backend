package com.theguy.app.entity;

import com.theguy.app.enums.WalletEntryType;
import com.theguy.app.enums.WalletReferenceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WalletTransaction extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletEntryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletReferenceType referenceType;

    @Column(nullable = false)
    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;
}
