package com.theguy.app.service;

import com.theguy.app.dto.MpesaRequest;
import com.theguy.app.entity.Payment;
import com.theguy.app.entity.User;
import com.theguy.app.enums.PaymentMethod;
import com.theguy.app.enums.PaymentStatus;
import com.theguy.app.repository.PaymentRepository;
import com.theguy.app.repository.UserRepository;
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
}