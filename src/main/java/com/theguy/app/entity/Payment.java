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
    @Index(name = "idx_payments_job", columnList = "jobId"),
    @Index(name = "idx_payments_customer", columnList = "customerId"),
    @Index(name = "idx_payments_provider", columnList = "providerId"),
    @Index(name = "idx_payments_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {
    
    @Column(nullable = false)
    private UUID jobId;
    
    @Column(nullable = false)
    private UUID customerId;
    
    @Column(nullable = false)
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
    
    private LocalDateTime paidAt;
    
    private LocalDateTime releasedAt;
    
    private LocalDateTime refundedAt;
    
    private String failureReason;
    
    private Integer retryCount = 0;
    
    @Version
    private Integer version;
}