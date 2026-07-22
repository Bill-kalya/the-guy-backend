package com.theguy.app.entity;

import com.theguy.app.enums.AuditActorType;
import com.theguy.app.enums.FinancialAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "financial_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FinancialAuditLog extends BaseEntity {

    @Column(nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialAction action;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private String description;
}
