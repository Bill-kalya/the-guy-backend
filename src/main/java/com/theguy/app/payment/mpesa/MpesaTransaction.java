package com.theguy.app.payment.mpesa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mpesa_transactions", indexes = {
    @Index(name = "idx_mpesa_checkout", columnList = "checkoutRequestId"),
    @Index(name = "idx_mpesa_receipt", columnList = "mpesaReceiptNumber"),
    @Index(name = "idx_mpesa_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpesaTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String checkoutRequestId;

    private String merchantRequestId;

    private String mpesaReceiptNumber;

    private String phoneNumber;

    private Double amount;

    private String reference;

    private Integer resultCode;

    private String resultDesc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MpesaTransactionStatus status = MpesaTransactionStatus.INITIATED;

    @Column(columnDefinition = "TEXT")
    private String callbackPayload;

    private String transactionDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
