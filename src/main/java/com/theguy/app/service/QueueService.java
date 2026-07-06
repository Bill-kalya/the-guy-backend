package com.theguy.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    public void enqueueJobDispatch(UUID jobId, List<UUID> providerIds) {
        try {
            DispatchMessage msg = new DispatchMessage(jobId, providerIds);
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.opsForList().leftPush("job_dispatch_queue", json);
        } catch (Exception e) {
            log.warn("Failed to enqueue job dispatch (Redis unavailable?): {}", e.getMessage());
        }
    }
    
    public record DispatchMessage(UUID jobId, List<UUID> providerIds) {}
}