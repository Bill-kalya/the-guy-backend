package com.theguy.app.service;

import com.theguy.app.dto.JobRequestDTO;
import com.theguy.app.dto.JobResponseDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.enums.Urgency;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.PriceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {
    
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PricingService pricingService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final LocationService locationService;
    private final PriceSnapshotService priceSnapshotService;
    
    @Transactional
    public Job requestJob(JobRequestDTO dto, User customer) {
        log.info("Creating new job request for customer: {}", customer.getId());
        
        // Get price estimate
        var priceEstimate = pricingService.estimate(
            dto.getCategory(), 
            dto.getLocation().getLatitude(), 
            dto.getLocation().getLongitude(),
            dto.getUrgency()
        );
        
        // Create job entity
        Job job = new Job();
        job.setCustomer(customer);
        job.setServiceCategory(dto.getCategory());
        job.setDescription(dto.getDescription());
        job.setStatus(JobStatus.REQUESTED);
        job.setUrgency(dto.getUrgency());
        job.setPriceEstimateMin(priceEstimate.getMinPrice());
        job.setPriceEstimateMax(priceEstimate.getMaxPrice());
        job.setLatitude(dto.getLocation().getLatitude());
        job.setLongitude(dto.getLocation().getLongitude());
        
        if (dto.getBudgetMin() != null) {
            job.setPriceEstimateMin(Math.max(job.getPriceEstimateMin(), dto.getBudgetMin()));
        }
        if (dto.getBudgetMax() != null) {
            job.setPriceEstimateMax(Math.min(job.getPriceEstimateMax(), dto.getBudgetMax()));
        }
        
        Job savedJob = jobRepository.save(job);
        log.info("Job created with ID: {}", savedJob.getId());
        
        // Capture price snapshot at booking time
        double platformFee = savedJob.getFinalPrice() != null ? savedJob.getFinalPrice() * 0.10 : 0.0;
        double taxAmount = (savedJob.getFinalPrice() != null ? savedJob.getFinalPrice() : 0.0) * 0.16;
        priceSnapshotService.capture(savedJob,
            savedJob.getFinalPrice() != null ? savedJob.getFinalPrice() : 0.0,
            platformFee, taxAmount, 0.0);
        
        // Start matching process asynchronously
        matchingService.startMatching(savedJob);
        
        // Notify customer
        notificationService.notifyCustomer(
            customer.getId().toString(),
            Map.of("type", "JOB_CREATED", "jobId", savedJob.getId(), "status", "MATCHING_STARTED")
        );
        
        return savedJob;
    }
    
    @Transactional
    public void acceptJob(UUID jobId, UUID providerId) {
        log.info("Provider {} attempting to accept job {}", providerId, jobId);
        
        // Lock the job row for update to prevent race conditions
        Job job = jobRepository.findByIdWithLock(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        if (job.getStatus() != JobStatus.MATCHING && job.getStatus() != JobStatus.REQUESTED) {
            throw new IllegalStateException("Job is no longer available for acceptance. Current status: " + job.getStatus());
        }
        
        if (job.getProvider() != null) {
            throw new IllegalStateException("Job already assigned to another provider");
        }
        
        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        // Assign job to provider
        job.setProvider(provider);
        job.setStatus(JobStatus.ASSIGNED);
        job.setAcceptedAt(LocalDateTime.now());
        
        // Set final price (use provider's proposed price if available)
        if (job.getProviderProposedPrice() != null) {
            job.setFinalPrice(job.getProviderProposedPrice());
        } else {
            job.setFinalPrice((job.getPriceEstimateMin() + job.getPriceEstimateMax()) / 2);
        }
        
        jobRepository.save(job);
        
        log.info("Job {} accepted by provider {}", jobId, providerId);
        
        // Notify both parties
        notificationService.notifyCustomer(
            job.getCustomer().getId().toString(),
            Map.of("type", "JOB_ACCEPTED", "jobId", jobId, "providerId", providerId)
        );
        
        notificationService.notifyProvider(
            providerId.toString(),
            Map.of("type", "JOB_ACCEPTED_SUCCESS", "jobId", jobId, "customer", job.getCustomer().getFullName())
        );
    }
    
    @Transactional
    public void startJob(UUID jobId, UUID providerId) {
        Job job = validateProviderJob(jobId, providerId);
        
        if (job.getStatus() != JobStatus.ASSIGNED) {
            throw new IllegalStateException("Job must be assigned before starting");
        }
        
        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);
        
        notificationService.notifyCustomer(
            job.getCustomer().getId().toString(),
            Map.of("type", "JOB_STARTED", "jobId", jobId)
        );
    }
    
    @Transactional
    public void completeJob(UUID jobId, UUID providerId) {
        Job job = validateProviderJob(jobId, providerId);
        
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Job must be in progress before completing");
        }
        
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        
        // Update provider stats
        Provider provider = job.getProvider();
        provider.setJobsCompleted(provider.getJobsCompleted() + 1);
        providerRepository.save(provider);
        
        notificationService.notifyCustomer(
            job.getCustomer().getId().toString(),
            Map.of("type", "JOB_COMPLETED", "jobId", jobId)
        );
    }
    
    @Transactional
    public void cancelJob(UUID jobId, UUID userId, String role, String reason) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        boolean isAuthorized = false;
        if ("PROVIDER".equals(role) && job.getProvider() != null && job.getProvider().getId().equals(userId)) {
            isAuthorized = true;
        } else if ("CUSTOMER".equals(role) && job.getCustomer().getId().equals(userId)) {
            isAuthorized = true;
        } else if ("ADMIN".equals(role)) {
            isAuthorized = true;
        }
        
        if (!isAuthorized) {
            throw new SecurityException("Not authorized to cancel this job");
        }
        
        if (job.getStatus() == JobStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed job");
        }
        
        JobStatus previousStatus = job.getStatus();
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
        
        // Update provider stats if they were assigned
        if (job.getProvider() != null && previousStatus == JobStatus.ASSIGNED) {
            Provider provider = job.getProvider();
            provider.setJobsCancelled(provider.getJobsCancelled() + 1);
            providerRepository.save(provider);
        }
        
        log.info("Job {} cancelled by {} with reason: {}", jobId, role, reason);
        
        notificationService.notifyBothParties(job, Map.of("type", "JOB_CANCELLED", "reason", reason));
    }
    
    private Job validateProviderJob(UUID jobId, UUID providerId) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        if (job.getProvider() == null || !job.getProvider().getId().equals(providerId)) {
            throw new SecurityException("Job not assigned to this provider");
        }
        
        return job;
    }
    
    @Transactional(readOnly = true)
    public List<JobResponseDTO> getAllJobs() {
        return jobRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobResponseDTO> getNearbyJobs(double lat, double lng) {
        // Use a default radius of 5000 meters for nearby search
        List<com.theguy.app.dto.NearbyProviderDTO> nearbyProviders = locationService.findNearbyProviders(lat, lng, 5000, null);
        List<UUID> providerIds = nearbyProviders.stream()
                .map(com.theguy.app.dto.NearbyProviderDTO::getId)
                .collect(Collectors.toList());

        return jobRepository.findByProviderIdIn(providerIds).stream()
                .filter(job -> job.getStatus() == JobStatus.REQUESTED || job.getStatus() == JobStatus.MATCHING)
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobResponseDTO> getHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Job> jobs = jobRepository.findByCustomerId(user.getId());
        jobs.addAll(jobRepository.findByProviderId(user.getId()));

        return jobs.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void acceptJob(UUID jobId) {
        // Simplified accept - just marks the job as accepted without assigning a specific provider
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.REQUESTED && job.getStatus() != JobStatus.MATCHING) {
            throw new IllegalStateException("Job is no longer available for acceptance");
        }

        job.setStatus(JobStatus.ASSIGNED);
        job.setAcceptedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job {} accepted", jobId);
    }

    @Transactional
    public void declineJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.ASSIGNED) {
            throw new IllegalStateException("Job must be assigned before declining");
        }

        job.setStatus(JobStatus.REQUESTED);
        job.setProvider(null);
        job.setAcceptedAt(null);
        jobRepository.save(job);

        log.info("Job {} declined, returned to available pool", jobId);
    }

    @Transactional
    public void updateStatus(UUID jobId, JobStatus status) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        job.setStatus(status);
        if (status == JobStatus.COMPLETED) {
            job.setCompletedAt(LocalDateTime.now());
        }
        jobRepository.save(job);

        log.info("Job {} status updated to {}", jobId, status);
    }

    @Transactional
    public void completeJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Job must be in progress before completing");
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job {} completed", jobId);
    }

    @Transactional(readOnly = true)
    public JobResponseDTO getJobDetails(UUID jobId, UUID userId, String role) {
        Job job = jobRepository.findByIdWithDetails(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        // Check authorization
        boolean isAuthorized = false;
        if (job.getCustomer().getId().equals(userId)) isAuthorized = true;
        if (job.getProvider() != null && job.getProvider().getId().equals(userId)) isAuthorized = true;
        if ("ADMIN".equals(role)) isAuthorized = true;
        
        if (!isAuthorized) {
            throw new SecurityException("Not authorized to view this job");
        }
        
        return mapToResponseDTO(job);
    }
    
    @Transactional(readOnly = true)
    public List<Job> getJobsByCustomer(UUID customerId) {
        return jobRepository.findByCustomerId(customerId);
    }

    private JobResponseDTO mapToResponseDTO(Job job) {
        return JobResponseDTO.builder()
            .id(job.getId())
            .serviceCategory(job.getServiceCategory())
            .description(job.getDescription())
            .status(job.getStatus())
            .urgency(job.getUrgency())
            .priceEstimateMin(job.getPriceEstimateMin())
            .priceEstimateMax(job.getPriceEstimateMax())
            .finalPrice(job.getFinalPrice())
            .location(JobResponseDTO.LocationDTO.builder()
                .latitude(job.getLatitude())
                .longitude(job.getLongitude())
                .build())
            .provider(job.getProvider() != null ? 
                JobResponseDTO.ProviderSummaryDTO.builder()
                    .id(job.getProvider().getId())
                    .fullName(job.getProvider().getUser().getFullName())
                    .rating(job.getProvider().getRatingAvg())
                    .jobsCompleted(job.getProvider().getJobsCompleted())
                    .verificationLevel(job.getProvider().getVerificationLevel().name())
                    .build() : null)
            .createdAt(job.getCreatedAt())
            .acceptedAt(job.getAcceptedAt())
            .completedAt(job.getCompletedAt())
            .build();
    }
}