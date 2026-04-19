package com.theguy.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Integer> redisTemplate;
    
    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 15;

    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        
        String clientIp = getClientIp(request);
        String key = "rate_limit:auth:" + clientIp;
        
        Integer attempts = redisTemplate.opsForValue().get(key);
        
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            response.setStatus(429);
            response.getWriter().write("Too many authentication attempts. Try again later.");
            return false;
        }
        
        return true;
    }
    
    public void recordFailedAttempt(String clientIp) {
        String key = "rate_limit:auth:" + clientIp;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(BLOCK_DURATION_MINUTES));
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}