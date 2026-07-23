package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Entity
@Table(name = "portfolio_images", indexes = {
    @Index(name = "idx_portfolio_provider", columnList = "provider_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class PortfolioImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String imageUrl;

    @Column(name = "public_id")
    private String publicId;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status")
    private ModerationStatus moderationStatus = ModerationStatus.PENDING_REVIEW;

    private Boolean isActive = true;

    public enum ModerationStatus {
        PENDING_REVIEW, APPROVED, REJECTED
    }
}
