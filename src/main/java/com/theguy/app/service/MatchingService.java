package com.theguy.app.service;

import com.theguy.app.dto.NearbyProviderDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.JobRequest;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.enums.JobRequestStatus;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.JobRequestRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository locationRepository;
    private final JobRepository jobRepository;
    private final JobRequestRepository jobRequestRepository;
    private final QueueService queueService;
    private final NotificationService notificationService;
    private final ProviderStatisticsService providerStatisticsService;

    /**
     * Starts matching for a job.
     *
     * @param targetProviderId when set, the job is offered only to this provider
     *                         (direct request); otherwise it is broadcast to the
     *                         best nearby providers.
     */
    @Transactional
    public void startMatching(Job job, UUID targetProviderId) {
        // Mark the job as being matched so the dispatch worker will process it.
        // saveAndFlush makes the status visible before the dispatch message is
        // enqueued, so the worker never reads a stale REQUESTED row.
        job.setStatus(JobStatus.MATCHING);
        jobRepository.saveAndFlush(job);

        List<Provider> candidates = resolveCandidates(job, targetProviderId);

        if (candidates.isEmpty()) {
            job.setStatus(JobStatus.CANCELLED);
            jobRepository.save(job);
            notificationService.notifyCustomer(job.getCustomer().getId().toString(),
                Map.of("type", "NO_PROVIDERS_AVAILABLE", "jobId", job.getId()));
            return;
        }

        // Step 2: Score and rank providers
        List<ProviderScore> ranked = candidates.stream()
            .map(p -> scoreProvider(p, job))
            .sorted(Comparator.comparingDouble(ProviderScore::score).reversed())
            .limit(3)
            .toList();

        // Step 3: Enqueue for dispatch
        queueService.enqueueJobDispatch(job.getId(), ranked.stream()
            .map(ps -> ps.provider().getId())
            .toList());
    }

    private List<Provider> resolveCandidates(Job job, UUID targetProviderId) {
        String category = job.getServiceCategory();

        if (targetProviderId != null) {
            Optional<Provider> target = providerRepository.findById(targetProviderId);
            if (target.isPresent() && isEligible(target.get(), job)) {
                log.info("Targeting provider {} for job {}", targetProviderId, job.getId());
                return List.of(target.get());
            }
            log.warn("Targeted provider {} invalid for job {} — falling back to broadcast",
                targetProviderId, job.getId());
        }

        return findBroadcastCandidates(job, category);
    }

    private List<Provider> findBroadcastCandidates(Job job, String category) {
        // Step 1: Find nearby providers using LocationService-compatible approach
        double radius = job.getUrgency() == com.theguy.app.enums.Urgency.INSTANT ? 5000 : 15000;

        com.theguy.app.utils.LocationUtils.BoundingBox bbox =
            com.theguy.app.utils.LocationUtils.getBoundingBox(job.getLatitude(), job.getLongitude(), radius);

        List<ProviderLocation> nearbyLocations = locationRepository.findNearbyProviders(
            job.getLatitude(), job.getLongitude(), radius,
            bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng
        );

        List<UUID> providerIds = nearbyLocations.stream()
            .map(ProviderLocation::getProviderId)
            .collect(Collectors.toList());

        // Filter candidates by the job's service category (case-insensitive)
        List<Provider> candidates;
        if (category != null && !category.isBlank()) {
            candidates = providerRepository.findByIdInAndCategoryIdIgnoreCase(providerIds, category);
        } else {
            candidates = providerRepository.findAllById(providerIds);
        }

        if (candidates.isEmpty()) {
            // Fallback: expand radius
            bbox = com.theguy.app.utils.LocationUtils.getBoundingBox(job.getLatitude(), job.getLongitude(), radius * 2);
            nearbyLocations = locationRepository.findNearbyProviders(
                job.getLatitude(), job.getLongitude(), radius * 2,
                bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng
            );
            providerIds = nearbyLocations.stream()
                .map(ProviderLocation::getProviderId)
                .collect(Collectors.toList());

            if (category != null && !category.isBlank()) {
                candidates = providerRepository.findByIdInAndCategoryIdIgnoreCase(providerIds, category);
            } else {
                candidates = providerRepository.findAllById(providerIds);
            }
        }

        // Only active providers, and skip providers who already declined this job.
        List<UUID> declined = jobRequestRepository.findByJobId(job.getId()).stream()
            .filter(r -> r.getStatus() == JobRequestStatus.REJECTED || r.getStatus() == JobRequestStatus.EXPIRED)
            .map(JobRequest::getProviderId)
            .collect(Collectors.toList());

        return candidates.stream()
            .filter(p -> isEligible(p, job))
            .filter(p -> !declined.contains(p.getId()))
            .toList();
    }

    private boolean isEligible(Provider p, Job job) {
        if (!"ACTIVE".equalsIgnoreCase(p.getProviderStatus())) return false;

        String category = job.getServiceCategory();
        if (category == null || category.isBlank()) return true;
        return p.getCategoryId() != null && p.getCategoryId().equalsIgnoreCase(category);
    }
    
    private ProviderScore scoreProvider(Provider p, Job job) {
        double distanceScore = 1.0; // Simplified - would calculate from actual distance
        double responseScore = p.getResponseRate();
        double priceScore = 1.0; // Simplified - compare to market avg
        double demandBoost = 1.0;
        
        // Get SQS (Service Quality Score) from cached statistics
        Optional<ProviderStatistics> statsOpt = providerStatisticsService.getStatistics(p.getId());
        double sqsScore = statsOpt.map(ProviderStatistics::getSqs).orElse(0.0) / 100.0; // Normalize to 0-1
        
        // Updated scoring formula with SQS
        // Distance: 40%, SQS: 35%, Response Rate: 15%, Price: 5%, Demand: 5%
        double finalScore = (0.40 * distanceScore) + 
                           (0.35 * sqsScore) + 
                           (0.15 * responseScore) + 
                           (0.05 * priceScore) + 
                           (0.05 * demandBoost);
        
        return new ProviderScore(p, finalScore);
    }
    
    record ProviderScore(Provider provider, double score) {}
}