package com.theguy.app.payment.stripe;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.theguy.app.payment.PaymentProvider;
import com.theguy.app.payment.PaymentResponse;
import com.theguy.app.payment.PaymentStatusResponse;
import com.theguy.app.payment.RefundResponse;
import com.stripe.model.Refund;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class StripePaymentProvider implements PaymentProvider {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isEmpty()) {
            Stripe.apiKey = secretKey;
        }
    }

    @Override
    public String getProviderName() {
        return "CARD";
    }

    @Override
    public PaymentResponse initiatePayment(BigDecimal amount, String currency,
                                            String reference, Map<String, Object> metadata) {
        log.info("Stripe initiating payment: amount={}, currency={}, ref={}", amount, currency, reference);

        if (secretKey == null || secretKey.isEmpty()) {
            return PaymentResponse.builder()
                .success(false)
                .message("Stripe is not configured. Please set STRIPE_SECRET_KEY.")
                .build();
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());
            params.put("currency", currency.toLowerCase());
            params.put("description", "Payment for " + reference);

            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("reference", reference);
            String jobId = (String) metadata.get("jobId");
            if (jobId != null) metadataMap.put("jobId", jobId);
            String paymentId = (String) metadata.get("paymentId");
            if (paymentId != null) metadataMap.put("paymentId", paymentId);
            params.put("metadata", metadataMap);

            PaymentIntent intent = PaymentIntent.create(params);

            return PaymentResponse.builder()
                .success(true)
                .transactionId(intent.getId())
                .providerReference(intent.getId())
                .clientSecret(intent.getClientSecret())
                .status(intent.getStatus())
                .message("PaymentIntent created successfully")
                .build();

        } catch (StripeException e) {
            log.error("Stripe payment initiation failed: {}", e.getMessage(), e);
            return PaymentResponse.builder()
                .success(false)
                .message("Card payment failed: " + e.getMessage())
                .build();
        }
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return PaymentStatusResponse.builder()
                .success(true)
                .transactionId(intent.getId())
                .status(mapStripeStatus(intent.getStatus()))
                .rawStatus(intent.getStatus())
                .build();
        } catch (StripeException e) {
            log.error("Stripe status check failed: {}", e.getMessage());
            return PaymentStatusResponse.builder()
                .success(false)
                .transactionId(paymentIntentId)
                .status("ERROR")
                .failureReason(e.getMessage())
                .build();
        }
    }

    @Override
    public RefundResponse refund(String paymentIntentId, BigDecimal amount) {
        log.info("Stripe refund requested: paymentIntent={}, amount={}", paymentIntentId, amount);

        try {
            Map<String, Object> refundParams = new HashMap<>();
            refundParams.put("payment_intent", paymentIntentId);
            refundParams.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());

            Refund refund = Refund.create(refundParams);

            return RefundResponse.builder()
                .success("succeeded".equals(refund.getStatus()))
                .status(refund.getStatus())
                .message("Refund " + refund.getId() + " status: " + refund.getStatus())
                .build();
        } catch (StripeException e) {
            log.error("Stripe refund failed: {}", e.getMessage(), e);
            return RefundResponse.builder()
                .success(false)
                .status("FAILED")
                .message("Refund failed: " + e.getMessage())
                .build();
        }
    }

    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            throw new SignatureVerificationException("No webhook secret configured", sigHeader);
        }
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    public Map<String, Object> parseWebhookEvent(Event event) {
        Map<String, Object> result = new HashMap<>();
        result.put("eventType", event.getType());

        if ("payment_intent.succeeded".equals(event.getType()) ||
            "payment_intent.payment_failed".equals(event.getType())) {

            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

            if (intent != null) {
                result.put("paymentIntentId", intent.getId());
                result.put("status", intent.getStatus());
                result.put("succeeded", "payment_intent.succeeded".equals(event.getType()));

                Map<String, String> metadata = intent.getMetadata();
                if (metadata != null) {
                    result.put("reference", metadata.get("reference"));
                    result.put("jobId", metadata.get("jobId"));
                    result.put("paymentId", metadata.get("paymentId"));
                }
            }
        }

        return result;
    }

    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> "HELD";
            case "processing" -> "PENDING";
            case "requires_payment_method", "requires_confirmation", "requires_action" -> "PENDING";
            case "canceled", "requires_capture" -> switch (stripeStatus) {
                case "canceled" -> "FAILED";
                default -> "PENDING";
            };
            default -> "PENDING";
        };
    }
}
