package com.theguy.app.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private boolean success;
    private String transactionId;
    private String status;
    private String rawStatus;
    private String failureReason;
}
