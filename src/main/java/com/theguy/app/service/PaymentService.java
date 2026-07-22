package com.theguy.app.service;

import com.theguy.app.dto.MpesaRequest;
import com.theguy.app.entity.Payment;
import com.theguy.app.entity.User;
import com.theguy.app.enums.AccountCode;
import com.theguy.app.enums.AuditActorType;
import com.theguy.app.enums.FinancialAction;
import com.theguy.app.enums.PaymentMethod;
import com.theguy.app.enums.PaymentStatus;
import com.theguy.app.enums.WalletReferenceType;
import com.theguy.app.repository.PaymentRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.WalletService;
import com.theguy.app.service.LedgerService;
import com.theguy.app.service.FinancialAuditLogService;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final FinancialAuditLogService auditLogService;
    private final com.theguy.app.repository.JobRepository jobRepository;
    private final com.theguy.app.repository.ProviderRepository providerRepository;

    @Transactional
    public Map<String, Object> initiate(MpesaRequest request) {
        log.info("Initiating M-Pesa payment for phone: {}", request.getPhoneNumber());

        // Create payment record
        Payment payment = new Payment();
        payment.setAmount(BigDecimal.valueOf(request.getAmount()));
        payment.setPaymentMethod(PaymentMethod.MPESA);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCheckoutRequestId(UUID.randomUUID().toString());
        payment.setTransactionReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        paymentRepository.save(payment);

        // TODO: Integrate with actual M-Pesa API (Daraja)
        // For now, simulate the initiation
        log.info("M-Pesa payment initiated with checkout ID: {}", payment.getCheckoutRequestId());

        Map<String, Object> response = new HashMap<>();
        response.put("checkoutRequestId", payment.getCheckoutRequestId());
        response.put("transactionReference", payment.getTransactionReference());
        response.put("amount", request.getAmount());
        response.put("phoneNumber", request.getPhoneNumber());
        response.put("status", "PENDING");
        response.put("message", "Please check your phone and enter M-Pesa PIN to complete payment");

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String checkoutId) {
        Payment payment = paymentRepository.findByCheckoutRequestId(checkoutId)
                .orElseThrow(() -> new RuntimeException("Payment not found with checkout ID: " + checkoutId));

        Map<String, Object> response = new HashMap<>();
        response.put("checkoutRequestId", payment.getCheckoutRequestId());
        response.put("transactionReference", payment.getTransactionReference());
        response.put("amount", payment.getAmount());
        response.put("status", payment.getStatus());
        response.put("mpesaReceiptNumber", payment.getMpesaReceiptNumber());
        response.put("paidAt", payment.getPaidAt());

        return response;
    }

    @Transactional(readOnly = true)
    public List<Payment> history(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Payment> payments = paymentRepository.findByCustomerId(user.getId());
        payments.addAll(paymentRepository.findByProviderId(user.getId()));

        return payments;
    }

    @Transactional
    public Map<String, Object> handlePaymentSuccess(String checkoutRequestId, String mpesaReceiptNumber) {
        Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(PaymentStatus.HELD);
        payment.setMpesaReceiptNumber(mpesaReceiptNumber);
        payment.setPaidAt(java.time.LocalDateTime.now());
        paymentRepository.save(payment);

        // Get the job and provider
        var job = jobRepository.findById(payment.getJobId()).orElse(null);
        if (job == null || job.getProvider() == null) {
            log.error("Payment success but no job/provider found for payment: {}", payment.getId());
            return Map.of("status", "SUCCESS", "message", "Payment recorded but job not yet assigned");
        }

        UUID providerId = job.getProvider().getId();
        double totalAmount = payment.getAmount().doubleValue();
        double platformFee = totalAmount * 0.10;
        double providerAmount = totalAmount - platformFee;

        // 1. Credit provider pending wallet
        walletService.creditPending(providerId, providerAmount, WalletReferenceType.JOB,
                job.getId(), "Payment received for job");

        // 2. Record double-entry ledger
        ledgerService.recordDoubleEntry(
                AccountCode.ESCROW, AccountCode.PLATFORM_REVENUE,
                platformFee, "KES", "PAYMENT", payment.getId(),
                "Platform fee (10%) for job");
        ledgerService.recordDoubleEntry(
                AccountCode.ESCROW, AccountCode.PROVIDER_EARNINGS,
                providerAmount, "KES", "PAYMENT", payment.getId(),
                "Provider earnings for job");

        // 3. Tax record (16% VAT on platform fee)
        double taxAmount = platformFee * 0.16;
        ledgerService.record(
                AccountCode.TAX_LIABILITY, com.theguy.app.enums.EntryType.CREDIT,
                taxAmount, "KES", "PAYMENT", payment.getId(), "VAT on platform fee");

        // 4. Audit log
        auditLogService.log(null, AuditActorType.SYSTEM, FinancialAction.PAYMENT_RECEIVED,
                "Payment", payment.getId(),
                String.format("Payment of KES %.2f received via %s", totalAmount, payment.getPaymentMethod()));

        log.info("Payment success processed: paymentId={}, jobId={}, provider={}", payment.getId(), job.getId(), providerId);

        return Map.of("status", "SUCCESS", "paymentId", payment.getId(), "jobId", job.getId());
    }
}