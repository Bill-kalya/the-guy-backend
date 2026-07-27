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

        // Calculate processor fee (M-Pesa: ~0.5% of transaction)
        double processorFeePct = 0.5;
        double processorFee = Math.round(totalAmount * processorFeePct * 100.0) / 100.0;
        payment.setProcessorFee(BigDecimal.valueOf(processorFee));
        payment.setProcessorFeePercentage(BigDecimal.valueOf(processorFeePct));

        // Revenue recognition: hold ALL funds in escrow until job completes.
        // At this point, no revenue is earned and no provider liability exists.
        ledgerService.recordDoubleEntry(
            AccountCode.ESCROW, AccountCode.CUSTOMER_PREPAID,
            totalAmount, "KES", "PAYMENT", payment.getId(),
            "Customer funds held in escrow for job");

        auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.ESCROW_FUNDED,
            "Payment", payment.getId(),
            String.format("Escrow funded: KES %.2f via %s (processor fee: KES %.2f) for job",
                totalAmount, payment.getPaymentMethod(), processorFee));

        log.info("Payment confirmed (escrow): id={}, amount={}, processorFee={}",
            payment.getId(), totalAmount, processorFee);
    }

    @Transactional
    public void releaseEscrowOnJobCompletion(UUID jobId, UUID providerId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.HELD) {
            log.warn("No HELD payment found for job completion: jobId={}", jobId);
            return;
        }

        double totalAmount = payment.getAmount().doubleValue();
        double processorFee = payment.getProcessorFee() != null ? payment.getProcessorFee().doubleValue() : 0.0;
        double netAmount = totalAmount - processorFee;
        double platformFee = netAmount * 0.10;
        double providerAmount = netAmount - platformFee;
        double taxAmount = platformFee * 0.16;

        // Move funds from escrow to provider pending wallet
        walletService.creditPending(providerId, providerAmount,
            WalletReferenceType.JOB, jobId,
            "Escrow released: provider earnings for completed job");

        // Platform revenue (net of processor fee)
        ledgerService.recordDoubleEntry(
            AccountCode.ESCROW, AccountCode.PLATFORM_REVENUE,
            platformFee, "KES", "JOB_COMPLETED", jobId,
            "Platform fee (10%) earned on job completion");

        ledgerService.recordDoubleEntry(
            AccountCode.ESCROW, AccountCode.PROVIDER_EARNINGS,
            providerAmount, "KES", "JOB_COMPLETED", jobId,
            "Provider earnings released on job completion");

        // Balanced tax entry: DEBIT PLATFORM_REVENUE, CREDIT TAX_LIABILITY
        ledgerService.recordDoubleEntry(
            AccountCode.PLATFORM_REVENUE, AccountCode.TAX_LIABILITY,
            taxAmount, "KES", "JOB_COMPLETED", jobId,
            "VAT (16%) on platform fee");

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.ESCROW_RELEASED,
            "Payment", paymentId,
            String.format("Escrow released: KES %.2f (processor: %.2f, platform: %.2f, provider: %.2f, tax: %.2f)",
                totalAmount, processorFee, platformFee, providerAmount, taxAmount));

        log.info("Escrow released: jobId={}, total={}, processor={}, platform={}, provider={}, tax={}",
            jobId, totalAmount, processorFee, platformFee, providerAmount, taxAmount);
    }

    @Transactional
    public void refundEscrowOnCancellation(UUID jobId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.HELD) {
            log.warn("No HELD payment found for cancellation refund: jobId={}", jobId);
            return;
        }

        double totalAmount = payment.getAmount().doubleValue();

        // Reverse the escrow entry: debit CUSTOMER_PREPAID, credit ESCROW
        ledgerService.recordDoubleEntry(
            AccountCode.CUSTOMER_PREPAID, AccountCode.ESCROW,
            totalAmount, "KES", "REFUND", jobId,
            "Escrow refunded: job cancelled");

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.REFUND_PROCESSED,
            "Payment", paymentId,
            String.format("Refund processed: KES %.2f for cancelled job", totalAmount));

        log.info("Escrow refunded: jobId={}, amount={}", jobId, totalAmount);
    }
}
