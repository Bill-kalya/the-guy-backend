package com.theguy.app.service;

import com.theguy.app.entity.Job;
import com.theguy.app.entity.Provider;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private final ProviderRepository providerRepository;
    private final JobRepository jobRepository;
    private final QueueService queueService;
    private final NotificationService notificationService;

    @Transactional
    public void startMatching(Job job) {
        // Step 1: Find nearby providers
        double radius = job.getUrgency() == com.theguy.app.enums.Urgency.INSTANT ? 5000 : 15000;
        List<Provider> candidates = providerRepository.findNearbyProviders(
            job.getLatitude(), job.getLongitude(), radius
        );
        
        if (candidates.isEmpty()) {
            // Fallback: expand radius
            candidates = providerRepository.findNearbyProviders(
                job.getLatitude(), job.getLongitude(), radius * 2
            );
        }
        
        // Step 2: Score and rank providers
        List<ProviderScore> ranked = candidates.stream()
            .map(p -> scoreProvider(p, job))
            .sorted(Comparator.comparingDouble(ProviderScore::score).reversed())
            .limit(3)
            .toList();
        
        if (ranked.isEmpty()) {
            job.setStatus(com.theguy.app.enums.JobStatus.CANCELLED);
            jobRepository.save(job);
            notificationService.notifyCustomer(job.getCustomer().getId().toString(), 
                Map.of("type", "NO_PROVIDERS_AVAILABLE", "jobId", job.getId()));
            return;
        }
        
        // Step 3: Enqueue for dispatch
        queueService.enqueueJobDispatch(job.getId(), ranked.stream()
            .map(ps -> ps.provider().getId())
            .toList());
    }
    
    private ProviderScore scoreProvider(Provider p, Job job) {
        double distanceScore = 1.0; // Simplified - would calculate from actual distance
        double reputationScore = p.getRatingAvg() / 5.0;
        double responseScore = p.getResponseRate();
        double priceScore = 1.0; // Simplified - compare to market avg
        double demandBoost = 1.0;
        
        double finalScore = (0.25 * distanceScore) + 
                           (0.30 * reputationScore) + 
                           (0.20 * responseScore) + 
                           (0.15 * priceScore) + 
                           (0.10 * demandBoost);
        
        return new ProviderScore(p, finalScore);
    }
    
    record ProviderScore(Provider provider, double score) {}
}