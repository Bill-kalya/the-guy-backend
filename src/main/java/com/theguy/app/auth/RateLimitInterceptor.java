package com.theguy.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    // Auth
    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 15;

    // OTP
    private static final int MAX_OTP_REQUESTS = 15;
    private static final long OTP_WINDOW_MINUTES = 60;

    private static final Set<String> OTP_PATHS = Set.of(
        "/api/auth/resend-otp",
        "/api/auth/forgot-password",
        "/api/auth/verify-reset-otp",
        "/api/auth/reset-password",
        "/api/auth/verify-email"
    );

    // General rate limit tiers: key prefix -> { max requests, window seconds }
    private static final Map<String, int[]> RATE_LIMIT_TIERS = Map.of(
        "upload",  new int[]{ 20,  3600 },  // 20/hour
        "review",  new int[]{ 30,  3600 },  // 30/hour
        "payment", new int[]{  5,    60 },  // 5/min
        "search",  new int[]{ 100,   60 },  // 100/min
        "location",new int[]{  4,    60 },  // 4/min (~every 15s)
        "general", new int[]{ 120,   60 }   // 120/min fallback
    );

    private static final Map<String, Set<String>> PATH_TO_TIER = Map.of(
        "upload",   Set.of("/api/files/upload"),
        "review",   Set.of("/api/reviews"),
        "payment",  Set.of("/api/payments"),
        "search",   Set.of("/api/search", "/api/providers/nearby"),
        "location", Set.of("/api/providers/location")
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {

        String uri = request.getRequestURI();

        if (OTP_PATHS.contains(uri)) {
            return handleOtpLimit(request, response);
        }

        if (uri.startsWith("/api/auth/")) {
            return handleAuthLimit(request, response);
        }

        String tier = resolveTier(uri);
        if (tier != null) {
            return handleTieredLimit(request, response, tier);
        }

        return true;
    }

    private String resolveTier(String uri) {
        for (var entry : PATH_TO_TIER.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (uri.startsWith(prefix)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean handleTieredLimit(HttpServletRequest request, HttpServletResponse response, String tier) {
        int[] config = RATE_LIMIT_TIERS.get(tier);
        int maxRequests = config[0];
        int windowSeconds = config[1];

        String clientIp = getClientIp(request);
        String key = "rate_limit:" + tier + ":" + clientIp;

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));

        try {
            String value = redisTemplate.opsForValue().get(key);
            int requests = value != null ? Integer.parseInt(value) : 0;

            int remaining = Math.max(0, maxRequests - requests);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            if (requests >= maxRequests) {
                log.warn("Rate limit [{}] exceeded for IP: {}", tier, clientIp);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(windowSeconds));
                response.getWriter().write("{\"error\":\"Too many requests. Rate limit: " + maxRequests + " per " + windowSeconds + "s\"}");
                return false;
            }

            Long incremented = redisTemplate.opsForValue().increment(key);
            if (incremented != null && incremented == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for [{}] rate limit: {}", tier, e.getMessage());
        }
        return true;
    }

    private boolean handleOtpLimit(HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String key = "rate_limit:otp:" + clientIp;

        try {
            String value = redisTemplate.opsForValue().get(key);
            int requests = value != null ? Integer.parseInt(value) : 0;
            if (requests >= MAX_OTP_REQUESTS) {
                log.warn("OTP rate limit exceeded for IP: {}", clientIp);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(OTP_WINDOW_MINUTES * 60));
                response.getWriter().write("Too many OTP requests. Try again later.");
                return false;
            }
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
