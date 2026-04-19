package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Entity
@Table(name = "matching_logs", indexes = {
    @Index(name = "idx_matching_logs_job", columnList = "job_id"),
    @Index(name = "idx_matching_logs_provider", columnList = "provider_id"),
    @Index(name = "idx_matching_logs_created", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class MatchingLog extends BaseEntity {
    
    @Column(nullable = false)
    private UUID jobId;
    
    @Column(nullable = false)
    private UUID providerId;
    
    @Column(precision = 10, scale = 6)
    private Double matchScore;
    
    @Column(precision = 10, scale = 6)
    private Double distanceScore;
    
    @Column(precision = 10, scale = 6)
    private Double reputationScore;
    
    @Column(precision = 10, scale = 6)
    private Double priceScore;
    
    @Column(precision = 10, scale = 6)
    private Double availabilityScore;
    
    @Column(precision = 10, scale = 6)
    private Double responsivenessScore;
    
    @Column(precision = 10, scale = 6)
    private Double demandBoostScore;
    
    private Boolean wasSelected = false;
    
    private Integer rankPosition;
    
    @Column(columnDefinition = "TEXT")
    private String algorithmVersion;
    
    private Long responseTimeMs;
    
    @Column(columnDefinition = "jsonb")
    private String debugData;
}