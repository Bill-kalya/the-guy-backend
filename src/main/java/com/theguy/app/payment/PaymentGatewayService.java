package com.theguy.app.payment;

import com.theguy.app.entity.Payment;
import com.theguy.app.enums.PaymentMethod;
import com.theguy.app.enums.PaymentStatus;
import com.theguy.app.repository.PaymentRepository;
import com.theguy.app.service.LedgerService;
import com.theguy.app.service.WalletService;
import com.theguy.app.service.FinancialAuditLogService;
import com.theguy.app.enums.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentGatewayService {

    private final List<PaymentProvider> providers;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final FinancialAuditLogService auditLogService;

    public PaymentGatewayService(List<PaymentProvider> providers,
                                  PaymentRepository paymentRepository,
                                  LedgerService ledgerService,
                                  WalletService walletService,
                                  FinancialAuditLogService auditLogService) {
        this.providers = providers;
        this.paymentRepository = paymentRepository;
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.auditLogService = auditLogService;
    }

    public PaymentProvider getProvider(PaymentMethod method) {
        String providerName = method.name();
        return providers.stream()
            .filter(p -> p.getProviderName().equals(providerName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No payment provider for: " + providerName));
    }

    @Transactional
    public PaymentResponse initiatePayment(UUID jobId, UUID customerId, UUID providerId,
                                            BigDecimal amount, String currency,
                                            PaymentMethod method, Map<String, Object> metadata) {
        PaymentProvider provider = getProvider(method);

        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = new Payment();
        payment.setJobId(jobId);
        payment.setCustomerId(customerId);
        payment.setProviderId(providerId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(method);
        payment.setTransactionReference(reference);
        payment = paymentRepository.save(payment);

        metadata.put("paymentId", payment.getId().toString());
        metadata.put("jobId", jobId.toString());

        PaymentResponse response = provider.initiatePayment(amount, currency, reference, metadata);

        if (response.isSuccess()) {
            payment.setCheckoutRequestId(response.getTransactionId());
            payment.setTransactionReference(response.getProviderReference());
            paymentRepository.save(payment);
        }

        log.info("Payment initiated: id={}, provider={}, method={}, amount={} {}",
            payment.getId(), provider.getProviderName(), method, amount, currency);

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPaymentStatus(String paymentId) {
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        PaymentProvider provider = getProvider(payment.getPaymentMethod());
        PaymentStatusResponse statusResponse = provider.getPaymentStatus(payment.getCheckoutRequestId());

        return Map.of(
            "paymentId", payment.getId(),
            "status", payment.getStatus(),
            "providerStatus", statusResponse.getStatus(),
            "amount", payment.getAmount(),
            "method", payment.getPaymentMethod()
        );
    }

    @Transactional
    public void confirmPaymentFromWebhook(String checkoutRequestId, String receiptNumber, boolean success) {
        Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
            .orElse(null);

        if (payment == null) {
            log.warn("Webhook for unknown checkout: {}", checkoutRequestId);
            return;
        }

        if (success) {
            payment.setStatus(PaymentStatus.HELD);
            payment.setMpesaReceiptNumber(receiptNumber);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            processSuccessfulPayment(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Webhook callback indicated failure");
            paymentRepository.save(payment);

            auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.PAYMENT_FAILED,
                "Payment", payment.getId(),
                "Payment failed via webhook: " + checkoutRequestId);
        }
    }

    private void processSuccessfulPayment(Payment payment) {
        double totalAmount = payment.getAmount().doubleValue();
        double platformFee = totalAmount * 0.10;
        double providerAmount = totalAmount - platformFee;

        walletService.creditPending(payment.getProviderId(), providerAmount,
            WalletReferenceType.JOB, payment.getJobId(),
            "Payment received for job via " + payment.getPaymentMethod());

        ledgerService.recordDoubleEntry(
            AccountCode.ESCROW, AccountCode.PLATFORM_REVENUE,
            platformFee, "KES", "PAYMENT", payment.getId(),
            "Platform fee (10%) for job");
        ledgerService.recordDoubleEntry(
            AccountCode.ESCROW, AccountCode.PROVIDER_EARNINGS,
            providerAmount, "KES", "PAYMENT", payment.getId(),
            "Provider earnings for job");

        double taxAmount = platformFee * 0.16;
        ledgerService.record(AccountCode.TAX_LIABILITY, EntryType.CREDIT,
            taxAmount, "KES", "PAYMENT", payment.getId(),
            "VAT on platform fee");

        auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.PAYMENT_RECEIVED,
            "Payment", payment.getId(),
            String.format("Payment of KES %.2f received via %s", totalAmount, payment.getPaymentMethod()));

        log.info("Payment confirmed: id={}, provider={}, amount={}", payment.getId(), payment.getProviderId(), totalAmount);
    }
}
