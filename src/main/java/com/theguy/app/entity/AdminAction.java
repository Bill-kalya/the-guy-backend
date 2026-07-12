package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "admin_actions",
        indexes = {
                @Index(name = "idx_admin_actions_admin", columnList = "admin_id"),
                @Index(name = "idx_admin_actions_timestamp", columnList = "created_at"),
                @Index(name = "idx_admin_actions_type", columnList = "action_type")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAction extends BaseEntity {

    @Column(nullable = false)
    private UUID adminId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(nullable = false)
    private String targetId; // User ID, Provider ID, Job ID, etc.

    @Column(nullable = false)
    private String targetType; // USER, PROVIDER, JOB, PAYMENT, etc.

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    private String ipAddress;
    private String userAgent;
    private String deviceId;

    private LocalDateTime timestamp;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    public enum ActionType {
            USER_SUSPEND, USER_UNSUSPEND, USER_BAN, USER_UNBAN,
            PROVIDER_APPROVE, PROVIDER_SUSPEND, PROVIDER_UNSUSPEND, PROVIDER_BAN,
            JOB_CANCEL, JOB_REASSIGN,
            PAYMENT_REFUND, PAYMENT_RELEASE,
            REVIEW_DELETE, REVIEW_HIDE,
            VERIFICATION_APPROVE, VERIFICATION_REJECT,
            DISPUTE_RESOLVE,
            SYSTEM_CONFIG_CHANGE,
            ROLE_CHANGE,
            FORCE_LOGOUT
    }
}

