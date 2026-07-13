package com.theguy.app.entity;

import com.theguy.app.enums.JobRequestStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_requests", indexes = {
    @Index(name = "idx_job_requests_job", columnList = "job_id"),
    @Index(name = "idx_job_requests_provider", columnList = "provider_id"),
    @Index(name = "idx_job_requests_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRequest extends BaseEntity {
    
@Column(name = "job_id", nullable = false)
    private UUID jobId;
    
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobRequestStatus status;
    
    @Column(nullable = false)
    private LocalDateTime sentAt;
    
    private LocalDateTime respondedAt;
    
    private Double proposedPrice;
    
    private String declineReason;
    
    private Integer retryNumber = 0;
    
    private Boolean isRead = false;
    
    private LocalDateTime readAt;
}