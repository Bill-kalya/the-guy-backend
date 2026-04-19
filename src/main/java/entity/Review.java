package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"job_id"}, name = "uk_review_job")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Review extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    private UUID jobId;
    
    @Column(nullable = false)
    private UUID customerId;
    
    @Column(nullable = false)
    private UUID providerId;
    
    @Column(nullable = false)
    private Integer ratingQuality;
    
    @Column(nullable = false)
    private Integer ratingReliability;
    
    @Column(nullable = false)
    private Integer ratingCommunication;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;
    
    @Column(nullable = false)
    private Boolean isVerifiedPurchase = true;
    
    @Column(nullable = false)
    private Boolean isHelpful = false;
    
    private Integer helpfulCount = 0;
    
    @PrePersist
    @PreUpdate
    public void validateRatings() {
        if (ratingQuality < 1 || ratingQuality > 5) {
            throw new IllegalArgumentException("Quality rating must be between 1 and 5");
        }
        if (ratingReliability < 1 || ratingReliability > 5) {
            throw new IllegalArgumentException("Reliability rating must be between 1 and 5");
        }
        if (ratingCommunication < 1 || ratingCommunication > 5) {
            throw new IllegalArgumentException("Communication rating must be between 1 and 5");
        }
    }
    
    public Double getAverageRating() {
        return (ratingQuality + ratingReliability + ratingCommunication) / 3.0;
    }
}