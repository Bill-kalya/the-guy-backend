package com.theguy.app.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.JobRequest;
import com.theguy.app.enums.JobRequestStatus;
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
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingWorker {
    private static final int RESPONSE_WINDOW_SECONDS = 45;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final JobRequestRepository jobRequestRepository;
    private final NotificationService notificationService;
    
    @Scheduled(fixedDelay = 2000)
    public void processDispatchQueue() {
        try {
            String messageJson = redisTemplate.opsForList().rightPop("job_dispatch_queue");
            if (messageJson == null) return;

            QueueService.DispatchMessage msg = objectMapper.readValue(
                messageJson, QueueService.DispatchMessage.class
            );
            dispatchToProviders(msg.jobId(), msg.providerIds());
        } catch (Exception e) {
            log.warn("Failed to process dispatch (Redis unavailable?): {}", e.getMessage());
        }
    }
    
    public void dispatchToProviders(UUID jobId, List<UUID> providerIds) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.MATCHING) return;
        
        // Send to top provider first (sequential for now)
        for (UUID providerId : providerIds) {
            JobRequest request = new JobRequest();
            request.setJobId(jobId);
            request.setProviderId(providerId);
            request.setStatus(JobRequestStatus.PENDING);
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
            
            if (job.getStatus() == JobStatus.ASSIGNED) {
                break;
            }
        }
    }

    /**
     * Cancels matching jobs where no provider responded within the response window.
     * The window is measured from the most recent dispatch (JobRequest.sentAt),
     * so re-dispatches after a decline get a fresh window.
     */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void expireTimedOutDispatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(RESPONSE_WINDOW_SECONDS);
        List<JobRequest> pending = jobRequestRepository.findByStatusAndSentAtBefore(
            JobRequestStatus.PENDING, cutoff);

        for (JobRequest stale : pending) {
            try {
                Job job = jobRepository.findById(stale.getJobId()).orElse(null);
                if (job == null || job.getStatus() != JobStatus.MATCHING) continue;

                log.info("No provider responded to job {} within {}s — cancelling",
                    job.getId(), RESPONSE_WINDOW_SECONDS);

                job.setStatus(JobStatus.CANCELLED);
                jobRepository.save(job);

                expirePendingRequests(job.getId());

                notificationService.notifyCustomer(job.getCustomer().getId().toString(),
                    Map.of("type", "NO_PROVIDER_ACCEPTED", "jobId", job.getId()));
            } catch (Exception e) {
                log.warn("Failed to expire dispatch for job {}: {}", stale.getJobId(), e.getMessage());
            }
        }
    }

    private void expirePendingRequests(UUID jobId) {
        for (JobRequest request : jobRequestRepository.findByJobId(jobId)) {
            if (request.getStatus() == JobRequestStatus.PENDING) {
                request.setStatus(JobRequestStatus.EXPIRED);
                request.setRespondedAt(LocalDateTime.now());
                jobRequestRepository.save(request);
            }
        }
    }
}