package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_statistics")
@Data
public class ProviderStatistics {
    
    @Id
    @Column(name = "provider_id")
    private UUID providerId;
    
    @Column(nullable = false)
    private Double sqs;
    
    @Column(nullable = false)
    private Double professionalismScore;
    
    @Column(nullable = false)
    private Double communicationScore;
    
    @Column(nullable = false)
    private Double timelinessScore;
    
    @Column(nullable = false)
    private Double workQualityScore;
    
    @Column(nullable = false)
    private Double valueScore;
    
    @Column(nullable = false)
    private Double reliabilityScore;
    
    @Column(nullable = false)
    private Double courtesyScore;
    
    @Column(nullable = false)
    private Integer reviewCount;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}