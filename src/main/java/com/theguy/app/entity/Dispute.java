package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "disputes",
        indexes = {
                @Index(name = "idx_disputes_job", columnList = "job_id"),
                @Index(name = "idx_disputes_status", columnList = "status"),
                @Index(name = "idx_disputes_assignee", columnList = "assignee_id")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class Dispute extends BaseEntity {

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisputeCategory category;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisputeStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisputePriority priority;

    private UUID assigneeId;

    private UUID resolutionId;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = DisputeStatus.OPEN;
        if (priority == null) priority = DisputePriority.MEDIUM;
    }

    public enum DisputeCategory {
        SERVICE_QUALITY,
        PAYMENT,
        CANCELLATION,
        NO_SHOW,
        DAMAGE,
        HARASSMENT,
        FRAUD,
        OTHER
    }

    public enum DisputeStatus {
        OPEN,
        IN_REVIEW,
        PENDING_EVIDENCE,
        AWAITING_RESPONSE,
        RESOLVED,
        CLOSED,
        APPEALED
    }

    public enum DisputePriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }
}

