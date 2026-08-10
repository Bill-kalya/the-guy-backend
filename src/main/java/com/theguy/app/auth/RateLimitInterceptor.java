package com.theguy.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    
    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 15;

    private static final int MAX_OTP_REQUESTS = 15;
    private static final long OTP_WINDOW_MINUTES = 60;

    private static final Set<String> OTP_PATHS = Set.of(
        "/api/auth/resend-otp",
        "/api/auth/forgot-password",
        "/api/auth/verify-reset-otp",
        "/api/auth/reset-password",
        "/api/auth/verify-email"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        
        if (OTP_PATHS.contains(request.getRequestURI())) {
            return handleOtpLimit(request, response);
        }
        return handleAuthLimit(request, response);
    }

    private boolean handleOtpLimit(HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String key = "rate_limit:otp:" + clientIp;

        int requests = 0;
        try {
            String value = redisTemplate.opsForValue().get(key);
            requests = value != null ? Integer.parseInt(value) : 0;
            if (requests >= MAX_OTP_REQUESTS) {
                log.warn("OTP rate limit exceeded for IP: {}", clientIp);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(OTP_WINDOW_MINUTES * 60));
                response.getWriter().write("Too many OTP requests. Try again later.");
                return false;
            }
            // Increment and (re)start the window on first request
            Long incremented = redisTemplate.opsForValue().increment(key);
            if (incremented != null && incremented == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(OTP_WINDOW_MINUTES));
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for OTP rate limit check: {}", e.getMessage());
        }
        return true;
    }

    private boolean handleAuthLimit(HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String key = "rate_limit:auth:" + clientIp;

        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_ATTEMPTS));
        
        int attempts = 0;
        try {
            String attemptsStr = redisTemplate.opsForValue().get(key);
            attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limit check: {}", e.getMessage());
        }

        int remaining = Math.max(0, MAX_ATTEMPTS - attempts);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        
        if (attempts >= MAX_ATTEMPTS) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(BLOCK_DURATION_MINUTES * 60));
            try {
                response.getWriter().write("Too many authentication attempts. Try again later.");
            } catch (Exception e) {
                log.error("Failed to write rate limit response", e);
            }
            return false;
        }
        
        return true;
    }
    
    public void recordFailedAttempt(String clientIp) {
        String key = "rate_limit:auth:" + clientIp;
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(BLOCK_DURATION_MINUTES));
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limit recording: {}", e.getMessage());
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
