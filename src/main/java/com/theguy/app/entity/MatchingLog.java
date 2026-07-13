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
    
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    
    @Column(name = "provider_id", nullable = false)
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
    
    // H2 compatibility: JSONB is not supported by H2 in tests.
    // In Postgres this will still store JSON as TEXT.
    @Column(columnDefinition = "TEXT")
    private String debugData;

}