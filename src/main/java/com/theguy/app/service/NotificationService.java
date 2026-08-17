package com.theguy.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.Notification;
import com.theguy.app.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final FcmService fcmService;
    private final ResendEmailService resendEmailService;

    @Async("notificationExecutor")
    public void sendJobToProvider(String providerId, Object payload) {
        try {
            String destination = "/queue/provider/" + providerId;
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Job notification sent to provider: {}", providerId);

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

            persistAndPush(customerId, payload, "customer");
        } catch (Exception e) {
            log.error("Failed to notify customer {}: {}", customerId, e.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void notifyProvider(String providerId, Object payload) {
        try {
            String destination = "/queue/provider/" + providerId;
            messagingTemplate.convertAndSend(destination, payload);

            persistAndPush(providerId, payload, "provider");
        } catch (Exception e) {
            log.error("Failed to notify provider {}: {}", providerId, e.getMessage());
        }
    }

    public void notifyBothParties(Job job, Map<String, Object> notification) {
        if (job.getCustomer() != null) {
            notifyCustomer(job.getCustomer().getId().toString(), notification);
        }
        if (job.getProvider() != null) {
            notifyProvider(job.getProvider().getUser().getId().toString(), notification);
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

    @Async("notificationExecutor")
    public void sendEmail(String to, String subject, String htmlBody, String textBody) {
        resendEmailService.sendOtpEmail(to, subject, htmlBody, textBody);
    }

    private void persistAndPush(String userId, Object payload, String role) {
        try {
            Map<String, Object> data = payload instanceof Map<?, ?> map
                ? (Map<String, Object>) (Object) map : Map.of();

            String type = data.getOrDefault("type", "UNKNOWN").toString();
            UUID jobId = null;
            if (data.get("jobId") instanceof UUID u) jobId = u;
            else if (data.get("jobId") instanceof String s) {
                try { jobId = UUID.fromString(s); } catch (Exception ignored) {}
            }

            Notification notification = new Notification();
            notification.setUserId(UUID.fromString(userId));
            notification.setType(type);
            notification.setTitle(buildTitle(type, data));
            notification.setBody(buildBody(type, data));
            notification.setReferenceId(jobId);
            notification.setReferenceType("JOB");
            notification.setChannel("IN_APP");
            notificationRepository.save(notification);

            String title = buildTitle(type, data);
            String body = buildBody(type, data);
            fcmService.sendToUser(userId, title, body, type,
                Map.of("type", type, "jobId", String.valueOf(data.getOrDefault("jobId", ""))));

        } catch (Exception e) {
            log.error("Failed to persist/push notification for user {}: {}", userId, e.getMessage());
        }
    }

    private String buildTitle(String type, Map<String, Object> data) {
        return switch (type) {
            case "JOB_CREATED" -> "Job Requested";
            case "JOB_ACCEPTED", "PROVIDER_ACCEPTED" -> "Job Accepted";
            case "JOB_ACCEPTED_SUCCESS" -> "Job Confirmed";
            case "JOB_STARTED" -> "Job In Progress";
            case "JOB_AWAITING_CONFIRMATION" -> "Job Completed — Confirm?";
            case "JOB_COMPLETED", "JOB_AUTO_CONFIRMED" -> "Job Completed";
            case "JOB_PAYMENT_RELEASED" -> "Payment Released";
            case "JOB_CANCELLED" -> "Job Cancelled";
            case "JOB_DISPUTED" -> "Job Disputed";
            case "NEW_JOB_REQUEST" -> "New Job Request";
            default -> "Notification";
        };
    }

    private String buildBody(String type, Map<String, Object> data) {
        return switch (type) {
            case "JOB_CREATED" -> "Your service request is being matched with providers.";
            case "JOB_ACCEPTED" -> "A provider has accepted your job.";
            case "JOB_ACCEPTED_SUCCESS" -> "You've been assigned this job. Get ready!";
            case "JOB_STARTED" -> "Your provider has started working on the job.";
            case "JOB_AWAITING_CONFIRMATION" -> "The provider marked the job as done. Please confirm.";
            case "JOB_COMPLETED" -> "The job has been completed successfully.";
            case "JOB_AUTO_CONFIRMED" -> "The job has been auto-confirmed.";
            case "JOB_PAYMENT_RELEASED" -> "Your earnings have been released to your wallet.";
            case "JOB_CANCELLED" -> "The job has been cancelled.";
            case "JOB_DISPUTED" -> "The job has been disputed. Please review.";
            case "NEW_JOB_REQUEST" -> "You have a new job request nearby.";
            default -> "";
        };
    }
}
