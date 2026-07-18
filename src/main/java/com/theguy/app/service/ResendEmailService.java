package com.theguy.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendEmailService {

    private final ObjectMapper objectMapper;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:no-reply@mail.theguy.co.ke}")
    private String fromEmail;

    @Value("${resend.from-name:TheGuy}")
    private String fromName;

    public void sendOtpEmail(String to, String subject, String htmlBody, String textBody) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key is not configured; skipping outbound email to {}", to);
            return;
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", String.format("%s <%s>", fromName, fromEmail));
            payload.put("to", new String[]{to});
            payload.put("subject", subject);
            payload.put("html", htmlBody);
            payload.put("text", textBody);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resendApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("Resend email failed for {} with status {} and body {}", to, response.statusCode(), response.body());
            } else {
                log.debug("Resend email sent to {} successfully", to);
            }
        } catch (Exception e) {
            log.error("Unable to send OTP email to {}: {}", to, e.getMessage(), e);
        }
    }
}
