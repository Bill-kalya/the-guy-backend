package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_achievements")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderAchievement extends BaseEntity {

    @Column(nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private String achievementId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false)
    private Boolean unlocked;

    private LocalDateTime unlockedAt;

    @Column(nullable = false)
    private Integer progress;

    @Column(nullable = false)
    private Integer target;
}
