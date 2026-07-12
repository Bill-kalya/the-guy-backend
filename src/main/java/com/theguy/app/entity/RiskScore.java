package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "risk_scores",
        indexes = {
                @Index(name = "idx_risk_scores_user", columnList = "user_id"),
                @Index(name = "idx_risk_scores_score", columnList = "score")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskScore extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userType; // CUSTOMER, PROVIDER

    @Column(nullable = false)
    private Integer score; // 0-100

    @Column(nullable = false)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, columnDefinition = "JSONB")
    private String factors; // JSON object with all risk factors

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    private LocalDateTime calculatedAt;

    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        calculatedAt = LocalDateTime.now();
        expiresAt = calculatedAt.plusDays(7);
    }
}

