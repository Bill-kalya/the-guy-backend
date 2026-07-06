package com.theguy.app.entity;

import com.theguy.app.enums.JobStatus;
import com.theguy.app.enums.Urgency;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@EqualsAndHashCode(callSuper = true)
public class Job extends BaseEntity {
    @ManyToOne
    private User customer;
    
    @ManyToOne
    private Provider provider;
    
    private String serviceCategory;
    private String description;
    
    @Enumerated(EnumType.STRING)
    private JobStatus status;
    
    @Enumerated(EnumType.STRING)
    private Urgency urgency;
    
    private LocalDateTime scheduledTime;
    
    private Double priceEstimateMin;
    private Double priceEstimateMax;
    private Double providerProposedPrice;
    private Double finalPrice;
    
    private Double latitude;
    private Double longitude;
    
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    
    @Version
    private Integer version;
}