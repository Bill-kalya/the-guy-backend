package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_reputation")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderReputation extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private UUID providerId;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private Double sqsContribution;

    @Column(nullable = false)
    private Double completionContribution;

    @Column(nullable = false)
    private Double responseContribution;

    @Column(nullable = false)
    private Double consistencyBonus;

    @Column(nullable = false)
    private Double cancellationPenalty;

    @Column(nullable = false)
    private Double disputePenalty;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.calculatedAt = LocalDateTime.now();
    }
}
