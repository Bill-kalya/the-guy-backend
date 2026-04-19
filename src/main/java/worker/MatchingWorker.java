package com.theguy.app.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.JobRequest;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.JobRequestRepository;
import com.theguy.app.service.NotificationService;
import com.theguy.app.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingWorker {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final JobRequestRepository jobRequestRepository;
    private final NotificationService notificationService;
    
    @Scheduled(fixedDelay = 2000)
    public void processDispatchQueue() {
        String messageJson = redisTemplate.opsForList().rightPop("job_dispatch_queue");
        if (messageJson == null) return;
        
        try {
            QueueService.DispatchMessage msg = objectMapper.readValue(
                messageJson, QueueService.DispatchMessage.class
            );
            dispatchToProviders(msg.jobId(), msg.providerIds());
        } catch (Exception e) {
            log.error("Failed to process dispatch", e);
        }
    }
    
    @Transactional
    public void dispatchToProviders(UUID jobId, List<UUID> providerIds) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.MATCHING) return;
        
        // Send to top provider first (sequential for now)
        for (UUID providerId : providerIds) {
            JobRequest request = new JobRequest();
            request.setJobId(jobId);
            request.setProviderId(providerId);
            request.setStatus(JobRequest.Status.PENDING);
            request.setSentAt(LocalDateTime.now());
            jobRequestRepository.save(request);
            
            notificationService.sendJobToProvider(
                providerId.toString(),
                Map.of(
                    "jobId", jobId,
                    "description", job.getDescription(),
                    "priceEstimateMin", job.getPriceEstimateMin(),
                    "priceEstimateMax", job.getPriceEstimateMax(),
                    "urgency", job.getUrgency()
                )
            );
            
            // Wait for response (simplified - in real system, use Redis delayed queue)
            try { Thread.sleep(15000); } catch (InterruptedException e) {}
            
            if (job.getStatus() == JobStatus.ASSIGNED) {
                break; // Job taken
            }
        }
        
        // If still no one accepted, mark as failed
        if (job.getStatus() == JobStatus.MATCHING) {
            job.setStatus(JobStatus.CANCELLED);
            jobRepository.save(job);
            notificationService.notifyCustomer(job.getCustomer().getId().toString(),
                Map.of("type", "NO_PROVIDER_ACCEPTED", "jobId", jobId));
        }
    }
}