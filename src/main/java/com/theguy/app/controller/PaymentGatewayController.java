package com.theguy.app.controller;

import com.theguy.app.payment.PaymentGatewayService;
import com.theguy.app.payment.PaymentProvider;
import com.theguy.app.payment.PaymentResponse;
import com.theguy.app.payment.mpesa.MpesaPaymentProvider;
import com.theguy.app.payment.mpesa.MpesaTransaction;
import com.theguy.app.payment.mpesa.MpesaTransactionStatus;
import com.theguy.app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    @PostMapping("/initiate")
    public ResponseEntity<?> initiatePayment(@RequestBody Map<String, Object> request) {
        try {
            UUID jobId = UUID.fromString((String) request.get("jobId"));
            UUID customerId = UUID.fromString((String) request.get("customerId"));
            UUID providerId = UUID.fromString((String) request.get("providerId"));
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String currency = (String) request.getOrDefault("currency", "KES");
            String method = (String) request.getOrDefault("method", "MPESA");

            com.theguy.app.enums.PaymentMethod paymentMethod =
                com.theguy.app.enums.PaymentMethod.valueOf(method.toUpperCase());

            Map<String, Object> metadata = Map.of(
                "phoneNumber", request.getOrDefault("phoneNumber", ""),
                "description", request.getOrDefault("description", "")
            );

            PaymentResponse response = gatewayService.initiatePayment(
                jobId, customerId, providerId, amount, currency, paymentMethod, metadata
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Payment initiation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId) {
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
