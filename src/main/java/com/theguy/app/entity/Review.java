package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class Review extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    private UUID jobId;
    
    @Column(nullable = false)
    private UUID customerId;
    
    @Column(nullable = false)
    private UUID providerId;
    
    // SQS fields (0-100 scale)
    @Column(nullable = false)
    private Integer overallExperience;
    
    @Column(nullable = false)
    private Integer timeliness;
    
    @Column(nullable = false)
    private Integer professionalism;
    
    @Column(nullable = false)
    private Integer communication;
    
    @Column(nullable = false)
    private Integer courtesy;
    
    @Column(nullable = false)
    private Integer workQuality;
    
    @Column(nullable = false)
    private Integer attentionToDetail;
    
    @Column(nullable = false)
    private Integer cleanliness;
    
    @Column(nullable = false)
    private Integer reliability;
    
    @Column(nullable = false)
    private Integer valueForMoney;
    
    @Column
    private Integer problemResolution;
    
    @Column(nullable = false)
    private Integer recommendation;
    
    @Column(nullable = false)
    private Double serviceQualityScore;
    
    @Column(columnDefinition = "TEXT")
    private String comment;
    
    @Column(nullable = false)
    @Default
    private Integer helpfulCount = 0;
    
    @PrePersist
    @PreUpdate
    public void validateRatings() {
        if (overallExperience == null || overallExperience < 0 || overallExperience > 100) {
            throw new IllegalArgumentException("Overall experience must be between 0 and 100");
        }
        if (timeliness == null || timeliness < 0 || timeliness > 100) {
            throw new IllegalArgumentException("Timeliness must be between 0 and 100");
        }
        if (professionalism == null || professionalism < 0 || professionalism > 100) {
            throw new IllegalArgumentException("Professionalism must be between 0 and 100");
        }
        if (communication == null || communication < 0 || communication > 100) {
            throw new IllegalArgumentException("Communication must be between 0 and 100");
        }
        if (courtesy == null || courtesy < 0 || courtesy > 100) {
            throw new IllegalArgumentException("Courtesy must be between 0 and 100");
        }
        if (workQuality == null || workQuality < 0 || workQuality > 100) {
            throw new IllegalArgumentException("Work quality must be between 0 and 100");
        }
        if (attentionToDetail == null || attentionToDetail < 0 || attentionToDetail > 100) {
            throw new IllegalArgumentException("Attention to detail must be between 0 and 100");
        }
        if (cleanliness == null || cleanliness < 0 || cleanliness > 100) {
            throw new IllegalArgumentException("Cleanliness must be between 0 and 100");
        }
        if (reliability == null || reliability < 0 || reliability > 100) {
            throw new IllegalArgumentException("Reliability must be between 0 and 100");
        }
        if (valueForMoney == null || valueForMoney < 0 || valueForMoney > 100) {
            throw new IllegalArgumentException("Value for money must be between 0 and 100");
        }
        if (problemResolution != null && (problemResolution < 0 || problemResolution > 100)) {
            throw new IllegalArgumentException("Problem resolution must be between 0 and 100");
        }
        if (recommendation == null || recommendation < 0 || recommendation > 100) {
            throw new IllegalArgumentException("Recommendation must be between 0 and 100");
        }
    }
}