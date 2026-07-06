package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Entity
@Table(name = "matching_logs", indexes = {
    @Index(name = "idx_matching_logs_job", columnList = "jobId"),
    @Index(name = "idx_matching_logs_provider", columnList = "providerId"),
    @Index(name = "idx_matching_logs_created", columnList = "createdAt")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class MatchingLog extends BaseEntity {
    
    @Column(nullable = false)
    private UUID jobId;
    
    @Column(nullable = false)
    private UUID providerId;
    
    @Column
    private Double matchScore;
    
    @Column
    private Double distanceScore;
    
    @Column
    private Double reputationScore;
    
    @Column
    private Double priceScore;
    
    @Column
    private Double availabilityScore;
    
    @Column
    private Double responsivenessScore;
    
    @Column
    private Double demandBoostScore;
    
    private Boolean wasSelected = false;
    
    private Integer rankPosition;
    
    @Column(columnDefinition = "TEXT")
    private String algorithmVersion;
    
    private Long responseTimeMs;
    
    @Column(columnDefinition = "jsonb")
    private String debugData;
}