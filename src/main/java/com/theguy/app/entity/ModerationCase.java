package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "moderation_cases",
        indexes = {
                @Index(name = "idx_moderation_status", columnList = "status"),
                @Index(name = "idx_moderation_assignee", columnList = "assignee_id")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class ModerationCase extends BaseEntity {

    @Column(nullable = false)
    private UUID reportedBy;

    @Column(nullable = false)
    private UUID reportedUser;

    @Column(nullable = false)
    private String contentType; // MESSAGE, REVIEW, PROFILE, SERVICE

    private UUID contentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModerationStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModerationPriority priority;

    private UUID assigneeId;

    @Column(columnDefinition = "TEXT")
    private String actionTaken;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime escalatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ModerationStatus.PENDING;
        if (priority == null) priority = ModerationPriority.MEDIUM;
    }

    public enum ModerationStatus {
        PENDING,
        IN_REVIEW,
        ESCALATED,
        RESOLVED,
        REJECTED,
        CLOSED
    }

    public enum ModerationPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}

