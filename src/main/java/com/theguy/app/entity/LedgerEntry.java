package com.theguy.app.entity;

import com.theguy.app.enums.AccountCode;
import com.theguy.app.enums.EntryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LedgerEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountCode accountCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(nullable = false)
    private String referenceType;

    @Column(nullable = false)
    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;
}
