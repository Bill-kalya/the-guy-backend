package com.theguy.app.payment.mpesa;

import com.theguy.app.payment.PaymentProvider;
import com.theguy.app.payment.PaymentResponse;
import com.theguy.app.payment.PaymentStatusResponse;
import com.theguy.app.payment.RefundResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MpesaPaymentProvider implements PaymentProvider {

    @Value("${mpesa.consumer-key:}")
    private String consumerKey;

    @Value("${mpesa.consumer-secret:}")
    private String consumerSecret;

    @Value("${mpesa.short-code:}")
    private String shortCode;

    @Value("${mpesa.pass-key:}")
    private String passKey;

    @Value("${mpesa.callback-url:}")
    private String callbackUrl;

    @Value("${mpesa.environment:sandbox}")
    private String environment;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, MpesaTransaction> transactionStore = new ConcurrentHashMap<>();

    private String getBaseUrl() {
        return "sandbox".equals(environment)
            ? "https://sandbox.safaricom.co.ke"
            : "https://api.safaricom.co.ke";
    }

    @Override
    public String getProviderName() {
        return "MPESA";
    }

    @Override
    public PaymentResponse initiatePayment(BigDecimal amount, String currency,
                                            String reference, Map<String, Object> metadata) {
        log.info("M-Pesa initiating payment: amount={}, ref={}", amount, reference);

        String phoneNumber = (String) metadata.getOrDefault("phoneNumber", "");
        if (phoneNumber.isEmpty()) {
            return PaymentResponse.builder()
                .success(false)
                .message("Phone number required for M-Pesa payment")
                .build();
        }

        try {
            String accessToken = getAccessToken();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = Base64.getEncoder().encodeToString(
                (shortCode + passKey + timestamp).getBytes()
            );

            Map<String, Object> stkPushRequest = new HashMap<>();
            stkPushRequest.put("BusinessShortCode", shortCode);
            stkPushRequest.put("Password", password);
            stkPushRequest.put("Timestamp", timestamp);
            stkPushRequest.put("TransactionType", "CustomerPayBillOnline");
            stkPushRequest.put("Amount", amount.intValue());
            stkPushRequest.put("PartyA", phoneNumber);
            stkPushRequest.put("PartyB", shortCode);
            stkPushRequest.put("PhoneNumber", phoneNumber);
            stkPushRequest.put("CallBackURL", callbackUrl);
            stkPushRequest.put("AccountReference", reference);
            stkPushRequest.put("TransactionDesc", "Payment for " + reference);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(stkPushRequest, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/mpesa/stkpush/v1/processrequest",
                HttpMethod.POST, entity, Map.class
            );

            if (response.getBody() != null && "0".equals(String.valueOf(response.getBody().get("ResponseCode")))) {
                String checkoutRequestId = (String) response.getBody().get("CheckoutRequestID");

                MpesaTransaction txn = MpesaTransaction.builder()
                    .id(UUID.randomUUID())
                    .checkoutRequestId(checkoutRequestId)
                    .merchantRequestId((String) response.getBody().get("MerchantRequestID"))
                    .phoneNumber(phoneNumber)
                    .amount(amount.doubleValue())
                    .status(MpesaTransactionStatus.INITIATED)
                    .reference(reference)
                    .createdAt(LocalDateTime.now())
                    .build();
                transactionStore.put(checkoutRequestId, txn);

                return PaymentResponse.builder()
                    .success(true)
                    .transactionId(checkoutRequestId)
                    .providerReference(checkoutRequestId)
                    .status("PENDING")
                    .message("STK Push sent. Please enter your M-Pesa PIN.")
                    .build();
            }

            String errorMessage = response.getBody() != null
                ? (String) response.getBody().get("ResponseDescription")
                : "Unknown error";

            return PaymentResponse.builder()
                .success(false)
                .message(errorMessage)
                .build();

        } catch (Exception e) {
            log.error("M-Pesa initiation failed: {}", e.getMessage(), e);
            return PaymentResponse.builder()
                .success(false)
                .message("M-Pesa service unavailable: " + e.getMessage())
                .build();
        }
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(String transactionId) {
        MpesaTransaction txn = transactionStore.get(transactionId);
        if (txn == null) {
            return PaymentStatusResponse.builder()
                .success(false)
                .transactionId(transactionId)
                .status("NOT_FOUND")
                .build();
        }

        return PaymentStatusResponse.builder()
            .success(true)
            .transactionId(txn.getId().toString())
            .status(txn.getStatus().name())
            .rawStatus(txn.getStatus().name())
            .build();
    }

    @Override
    public RefundResponse refund(String transactionId, BigDecimal amount) {
        log.warn("M-Pesa refund requested for txn={}, amount={}. Manual processing required.", transactionId, amount);
        return RefundResponse.builder()
            .success(false)
            .status("MANUAL_REQUIRED")
            .message("M-Pesa refunds require manual processing via Safaricom portal")
            .build();
    }

    public MpesaTransaction processCallback(Map<String, Object> callbackBody) {
        try {
            Map<String, Object> stkCallback = (Map<String, Object>) callbackBody.get("Body");
            if (stkCallback == null) stkCallback = callbackBody;

            Map<String, Object> callback = (Map<String, Object>) stkCallback.get("stkCallback");
            if (callback == null) callback = stkCallback;

            String merchantRequestId = (String) callback.get("MerchantRequestID");
            String checkoutRequestId = (String) callback.get("CheckoutRequestID");
            int resultCode = ((Number) callback.get("ResultCode")).intValue();
            String resultDesc = (String) callback.get("ResultDesc");

            MpesaTransaction txn = transactionStore.get(checkoutRequestId);
            if (txn == null) {
                txn = MpesaTransaction.builder()
                    .id(UUID.randomUUID())
                    .checkoutRequestId(checkoutRequestId)
                    .merchantRequestId(merchantRequestId)
                    .status(MpesaTransactionStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();
                transactionStore.put(checkoutRequestId, txn);
            }

            txn.setCallbackPayload(objectMapper.writeValueAsString(callbackBody));
            txn.setResultCode(resultCode);
            txn.setResultDesc(resultDesc);

            if (resultCode == 0) {
                List<Map<String, Object>> callbackMetadata = (List<Map<String, Object>>)
                    ((Map<String, Object>) callback.get("CallbackMetadata")).get("Item");

                for (Map<String, Object> item : callbackMetadata) {
                    String name = (String) item.get("Name");
                    if ("MpesaReceiptNumber".equals(name)) {
                        txn.setMpesaReceiptNumber((String) item.get("Value"));
                    } else if ("TransactionDate".equals(name)) {
                        txn.setTransactionDate(String.valueOf(item.get("Value")));
                    } else if ("PhoneNumber".equals(name)) {
                        txn.setPhoneNumber(String.valueOf(item.get("Value")));
                    }
                }
                txn.setStatus(MpesaTransactionStatus.SUCCESS);
                log.info("M-Pesa callback SUCCESS: checkout={}, receipt={}", checkoutRequestId, txn.getMpesaReceiptNumber());
            } else {
                txn.setStatus(MpesaTransactionStatus.FAILED);
                log.warn("M-Pesa callback FAILED: checkout={}, code={}, desc={}", checkoutRequestId, resultCode, resultDesc);
            }

            transactionStore.put(checkoutRequestId, txn);
            return txn;

        } catch (Exception e) {
            log.error("Error processing M-Pesa callback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process M-Pesa callback", e);
        }
    }

    private String getAccessToken() {
        String credentials = Base64.getEncoder().encodeToString(
            (consumerKey + ":" + consumerSecret).getBytes()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + credentials);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
            HttpMethod.GET, entity, Map.class
        );

        return (String) response.getBody().get("access_token");
    }
}
