package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_goals")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderGoal extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private UUID providerId;

    @Column(nullable = false)
    private Double weeklyTarget;

    @Column(nullable = false)
    private Double weeklyProgress;

    @Column(nullable = false)
    private Integer weekNumber;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
