package com.theguy.app.service;

import com.theguy.app.enums.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final ResendEmailService resendEmailService;

    @Value("${otp.hash-secret:default-otp-secret}")
    private String otpHashSecret;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SEND_PER_WINDOW = 3;
    private static final Duration MAX_VERIFY_BLOCK = Duration.ofMinutes(15);
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    public void generateAndSendOtp(String email, OtpPurpose purpose) {
        String otp = generateOtpCode();
        String hashedOtp = hashOtp(email, otp);

        ValueOperations<String, String> values = redisTemplate.opsForValue();
        String otpKey = buildOtpKey(email, purpose);
        String cooldownKey = buildSendCooldownKey(email, purpose);
        String sendCountKey = buildSendCountKey(email, purpose);

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
                throw new RuntimeException("OTP send cooldown in effect");
            }

            Long sendCount = values.increment(sendCountKey);
            if (sendCount == null) {
                throw new RuntimeException("Unable to calculate OTP send count");
            }
            if (sendCount == 1) {
                redisTemplate.expire(sendCountKey, SEND_WINDOW);
            }
            if (sendCount > MAX_SEND_PER_WINDOW) {
                throw new RuntimeException("OTP send limit exceeded");
            }

            values.set(cooldownKey, "1", SEND_COOLDOWN);
            values.set(otpKey, hashedOtp, OTP_TTL);
            log.debug("Stored OTP for {} with purpose {}", email, purpose);

            String subject = buildSubject(purpose);
            String html = buildHtmlBody(email, otp, purpose);
            String text = buildTextBody(otp, purpose);
            resendEmailService.sendOtpEmail(email, subject, html, text);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("cooldown") || e.getMessage().contains("limit")) {
                throw e;
            }
            log.error("Failed to generate or send OTP for {}: {}", email, e.getMessage());
            throw new RuntimeException("Unable to send OTP at this time");
        }
    }

    public void resendOtp(String email, OtpPurpose purpose) {
        generateAndSendOtp(email, purpose);
    }

    public void verifyOtp(String email, String otp, OtpPurpose purpose) {
        String otpKey = buildOtpKey(email, purpose);
        String attemptsKey = buildAttemptsKey(email, purpose);
        ValueOperations<String, String> values = redisTemplate.opsForValue();

        String storedHash = values.get(otpKey);
        if (storedHash == null) {
            recordFailedAttempt(attemptsKey, values);
            throw new RuntimeException("Invalid or expired code");
        }

        if (!storedHash.equals(hashOtp(email, otp))) {
            recordFailedAttempt(attemptsKey, values);
            throw new RuntimeException("Invalid or expired code");
        }

        values.getOperations().delete(otpKey);
        values.getOperations().delete(attemptsKey);
    }

    private void recordFailedAttempt(String attemptsKey, ValueOperations<String, String> values) {
        Long attempts = values.increment(attemptsKey);
        if (attempts == null) {
            return;
        }
        if (attempts == 1) {
            redisTemplate.expire(attemptsKey, MAX_VERIFY_BLOCK);
        }
        if (attempts >= MAX_VERIFY_ATTEMPTS) {
            redisTemplate.expire(attemptsKey, MAX_VERIFY_BLOCK);
            throw new RuntimeException("Too many failed OTP attempts. Try again later.");
        }
    }

    private String generateOtpCode() {
        SecureRandom random = new SecureRandom();
        int value = random.nextInt(1_000_000);
        return String.format(Locale.US, "%06d", value);
    }

    private String hashOtp(String email, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(otpHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            String data = email.toLowerCase(Locale.US) + ":" + otp;
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            log.error("Failed to hash OTP: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to verify OTP");
        }
    }

    private String buildOtpKey(String email, OtpPurpose purpose) {
        return String.format("otp:%s:%s", purpose.name().toLowerCase(Locale.US), email.toLowerCase(Locale.US));
    }

    private String buildAttemptsKey(String email, OtpPurpose purpose) {
        return String.format("otp_attempts:%s:%s", purpose.name().toLowerCase(Locale.US), email.toLowerCase(Locale.US));
    }

    private String buildSendCooldownKey(String email, OtpPurpose purpose) {
        return String.format("otp_send_cooldown:%s:%s", purpose.name().toLowerCase(Locale.US), email.toLowerCase(Locale.US));
    }

    private String buildSendCountKey(String email, OtpPurpose purpose) {
        return String.format("otp_send_count:%s:%s", purpose.name().toLowerCase(Locale.US), email.toLowerCase(Locale.US));
    }

    private String buildSubject(OtpPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> "Your TheGuy verification code";
            case RESET_PASSWORD -> "Your TheGuy password reset code";
        };
    }

    private String buildHtmlBody(String email, String otp, OtpPurpose purpose) {
        String action = purpose == OtpPurpose.VERIFY_EMAIL ? "verify your email address" : "reset your password";
        return "<p>Hello,</p><p>Your TheGuy verification code is <strong>" + otp + "</strong>.</p>"
            + "<p>Enter this code in the app to " + action + ".</p>"
            + "<p>This code expires in 5 minutes.</p>";
    }

    private String buildTextBody(String otp, OtpPurpose purpose) {
        String action = purpose == OtpPurpose.VERIFY_EMAIL ? "verify your email address" : "reset your password";
        return String.format("Your TheGuy verification code is %s. Enter this code in the app to %s. This code expires in 5 minutes.", otp, action);
    }
}
