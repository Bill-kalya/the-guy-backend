package com.theguy.app.entity;

import com.theguy.app.enums.PaymentMethod;
import com.theguy.app.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_job", columnList = "job_id"),
    @Index(name = "idx_payments_customer", columnList = "customer_id"),
    @Index(name = "idx_payments_provider", columnList = "provider_id"),
    @Index(name = "idx_payments_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {
    
@Column(name = "job_id", nullable = false)
    private UUID jobId;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;
    
    @Column(unique = true)
    private String transactionReference;
    
    private String mpesaReceiptNumber;
    
    private String checkoutRequestId;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    private LocalDateTime paidAt;

    private LocalDateTime releasedAt;

    private LocalDateTime refundedAt;
    
    private String failureReason;
    
    private Integer retryCount = 0;

    @Column(name = "processor_fee", precision = 10, scale = 2)
    private BigDecimal processorFee = BigDecimal.ZERO;

    @Column(name = "processor_fee_percentage", precision = 5, scale = 2)
    private BigDecimal processorFeePercentage = BigDecimal.ZERO;
    
    @Version
    private Integer version;
}