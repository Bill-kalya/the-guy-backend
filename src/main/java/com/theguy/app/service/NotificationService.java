package com.theguy.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theguy.app.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Async("notificationExecutor")
    public void sendJobToProvider(String providerId, Object payload) {
        try {
            String destination = "/queue/provider/" + providerId;
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Job notification sent to provider: {}", providerId);
            
            // Store in Redis for offline fallback
            String redisKey = "offline_notification:provider:" + providerId;
            String jsonPayload = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().leftPush(redisKey, jsonPayload);
            redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("Failed to send job to provider {}: {}", providerId, e.getMessage());
        }
    }
    
    @Async("notificationExecutor")
    public void notifyCustomer(String customerId, Object payload) {
        try {
            String destination = "/queue/customer/" + customerId;
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Notification sent to customer: {}", customerId);
        } catch (Exception e) {
            log.error("Failed to notify customer {}: {}", customerId, e.getMessage());
        }
    }
    
    @Async("notificationExecutor")
    public void notifyProvider(String providerId, Object payload) {
        try {
            String destination = "/queue/provider/" + providerId;
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Notification sent to provider: {}", providerId);
        } catch (Exception e) {
            log.error("Failed to notify provider {}: {}", providerId, e.getMessage());
        }
    }
    
    public void notifyBothParties(Job job, Map<String, Object> notification) {
        if (job.getCustomer() != null) {
            notifyCustomer(job.getCustomer().getId().toString(), notification);
        }
        if (job.getProvider() != null) {
            notifyProvider(job.getProvider().getId().toString(), notification);
        }
    }
    
    @Async("notificationExecutor")
    public void sendToUser(String userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, destination, payload);
            log.debug("Message sent to user {} on {}", userId, destination);
        } catch (Exception e) {
            log.error("Failed to send message to user {}: {}", userId, e.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void broadcastToTopic(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend("/topic/" + topic, payload);
            log.debug("Broadcast to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast to topic {}: {}", topic, e.getMessage());
        }
    }
}
