package com.theguy.app.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentProvider {

    PaymentResponse initiatePayment(
        BigDecimal amount,
        String currency,
        String reference,
        Map<String, Object> metadata
    );

    PaymentStatusResponse getPaymentStatus(
        String transactionId
    );

    RefundResponse refund(
        String transactionId,
        BigDecimal amount
    );

    String getProviderName();
}
