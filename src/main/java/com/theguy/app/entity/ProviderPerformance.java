package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_performance")
@Data
public class ProviderPerformance {

    @Id
    @Column(name = "provider_id")
    private UUID providerId;

    @Column(nullable = false)
    private Double acceptanceRate;

    @Column(nullable = false)
    private Double completionRate;

    @Column(nullable = false)
    private Double cancellationRate;

    @Column(nullable = false)
    private Double responseRate;

    @Column(nullable = false)
    private String avgResponseTime;

    @Column(nullable = false)
    private Integer repeatCustomerCount;

    @Column(nullable = false)
    private String ranking;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.calculatedAt = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
