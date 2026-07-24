package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_insights")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderInsight extends BaseEntity {

    @Column(nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private String insightType;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Integer frequency;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.generatedAt = LocalDateTime.now();
    }
}
