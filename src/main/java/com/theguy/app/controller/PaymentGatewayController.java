package com.theguy.app.controller;

import com.theguy.app.entity.Job;
import com.theguy.app.entity.Payment;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.payment.PaymentGatewayService;
import com.theguy.app.payment.PaymentProvider;
import com.theguy.app.payment.PaymentResponse;
import com.theguy.app.payment.mpesa.MpesaPaymentProvider;
import com.theguy.app.payment.mpesa.MpesaTransaction;
import com.theguy.app.payment.mpesa.MpesaTransactionStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.PaymentRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentGatewayController {

    private final PaymentGatewayService gatewayService;
    private final MpesaPaymentProvider mpesaProvider;
    private final PaymentService paymentService;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PaymentRepository paymentRepository;

    private User requireUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> initiatePayment(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String jobIdStr = (String) request.get("jobId");
            UUID jobId = UUID.fromString(jobIdStr);

            Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobIdStr));

            User customer = requireUser(auth);
            if (!job.getCustomer().getId().equals(customer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Not authorized to pay for this job"
                ));
            }

            UUID customerId = customer.getId();
            UUID providerId = job.getProvider() != null ? job.getProvider().getId() : null;
            if (providerId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Job has no assigned provider"
                ));
            }

            BigDecimal amount = job.getFinalPrice() != null
                ? BigDecimal.valueOf(job.getFinalPrice())
                : BigDecimal.valueOf(job.getPriceEstimateMin() != null ? job.getPriceEstimateMin() : 0);

            String method = (String) request.getOrDefault("method", "MPESA");
            com.theguy.app.enums.PaymentMethod paymentMethod =
                com.theguy.app.enums.PaymentMethod.valueOf(method.toUpperCase());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("phoneNumber", request.getOrDefault("phoneNumber", ""));
            metadata.put("description", request.getOrDefault("description", "Payment for job " + jobIdStr));

            Map<String, Object> initiationResult = gatewayService.initiatePayment(
                jobId, customerId, providerId, amount, "KES", paymentMethod, metadata
            );

            PaymentResponse response = (PaymentResponse) initiationResult.get("paymentResponse");

            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("paymentId", initiationResult.get("paymentId"));
            result.put("checkoutRequestId", response.getTransactionId());
            result.put("message", response.getMessage());
            result.put("amount", amount.doubleValue());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Payment initiation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId, Authentication auth) {
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        User user = requireUser(auth);
        boolean isCustomer = payment.getCustomerId() != null && payment.getCustomerId().equals(user.getId());
        boolean isProvider = false;
        if (payment.getProviderId() != null) {
            isProvider = providerRepository.findByUserId(user.getId())
                .map(p -> p.getId().equals(payment.getProviderId()))
                .orElse(false);
        }
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        if (!isCustomer && !isProvider && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Not authorized to view this payment"
            ));
        }

        return ResponseEntity.ok(gatewayService.getPaymentStatus(paymentId));
    }

    @PostMapping("/webhooks/mpesa")
    public ResponseEntity<?> handleMpesaWebhook(@RequestBody Map<String, Object> body) {
        log.info("M-Pesa webhook received");

        try {
            MpesaTransaction txn = mpesaProvider.processCallback(body);

            boolean success = MpesaTransactionStatus.SUCCESS.equals(txn.getStatus());
            gatewayService.confirmPaymentFromWebhook(
                txn.getCheckoutRequestId(),
                txn.getMpesaReceiptNumber(),
                success
            );

            return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Success"));
        } catch (Exception e) {
            log.error("M-Pesa webhook processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("ResultCode", 1, "ResultDesc", e.getMessage()));
        }
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<?> handleStripeWebhook(@RequestBody Map<String, Object> body) {
        log.info("Stripe webhook received (placeholder)");
        return ResponseEntity.ok(Map.of("received", true));
    }

    @PostMapping("/webhooks/paypal")
    public ResponseEntity<?> handlePaypalWebhook(@RequestBody Map<String, Object> body) {
        log.info("PayPal webhook received (placeholder)");
        return ResponseEntity.ok(Map.of("received", true));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(Authentication auth) {
        return ResponseEntity.ok(paymentService.history(auth.getName()));
    }
}
