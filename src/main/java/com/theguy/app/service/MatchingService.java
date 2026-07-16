package com.theguy.app.service;

import com.theguy.app.dto.NearbyProviderDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository locationRepository;
    private final JobRepository jobRepository;
    private final QueueService queueService;
    private final NotificationService notificationService;
    private final ProviderStatisticsService providerStatisticsService;

    @Transactional
    public void startMatching(Job job) {
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
        
        // Filter candidates by the job's service category
        String category = job.getServiceCategory();
        List<Provider> candidates;
        if (category != null && !category.isBlank()) {
            candidates = providerRepository.findByIdInAndServiceCategory(providerIds, category);
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
                candidates = providerRepository.findByIdInAndServiceCategory(providerIds, category);
            } else {
                candidates = providerRepository.findAllById(providerIds);
            }
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