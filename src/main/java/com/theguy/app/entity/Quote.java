package com.theguy.app.entity;

import com.theguy.app.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Quote extends BaseEntity {

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.PENDING;

    private BigDecimal counterAmount;

    private String rejectionReason;

    private LocalDateTime respondedAt;

    private LocalDateTime expiresAt;

    @Version
    private Integer version;
}
