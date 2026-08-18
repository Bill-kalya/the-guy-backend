package com.theguy.app.service;

import com.theguy.app.dto.CompleteJobDTO;
import com.theguy.app.dto.JobRequestDTO;
import com.theguy.app.dto.JobResponseDTO;
import com.theguy.app.dto.NearbyJobDTO;
import com.theguy.app.dto.RejectCompletionDTO;
import com.theguy.app.entity.Dispute;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.JobRequest;
import com.theguy.app.utils.InputSanitizer;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.enums.DisputeStatus;
import com.theguy.app.enums.JobRequestStatus;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.enums.Urgency;
import com.theguy.app.repository.DisputeRepository;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.JobRequestRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.repository.PaymentRepository;
import com.theguy.app.service.PriceSnapshotService;
import com.theguy.app.payment.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final JobRequestRepository jobRequestRepository;
    private final ProviderLocationRepository locationRepository;
    private final ProviderStatisticsService providerStatisticsService;
    private final PricingService pricingService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final LocationService locationService;
    private final PriceSnapshotService priceSnapshotService;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentRepository paymentRepository;
    private final DisputeRepository disputeRepository;

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
        job.setDescription(InputSanitizer.stripHtml(dto.getDescription()));
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

        // Start matching process asynchronously — targeted when a provider is specified
        matchingService.startMatching(savedJob, dto.getProviderId());

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

        job.setProvider(provider);
        job.setStatus(JobStatus.ASSIGNED);
        job.setAcceptedAt(LocalDateTime.now());

        if (job.getProviderProposedPrice() != null) {
            job.setFinalPrice(job.getProviderProposedPrice());
        } else {
            job.setFinalPrice((job.getPriceEstimateMin() + job.getPriceEstimateMax()) / 2);
        }

        jobRepository.save(job);

        // Expire requests still pending for other providers — this provider won
        expirePendingRequests(jobId, providerId);

        double finalPrice = job.getFinalPrice() != null ? job.getFinalPrice() : 0.0;
        double platformFee = finalPrice * 0.10;
        double taxAmount = finalPrice * 0.16;
        priceSnapshotService.capture(job, finalPrice, platformFee, taxAmount, 0.0);

        log.info("Job {} accepted by provider {}", jobId, providerId);

        notificationService.notifyCustomer(
            job.getCustomer().getId().toString(),
            Map.of("type", "JOB_ACCEPTED", "jobId", jobId, "providerId", providerId,
                   "provider", buildProviderSummary(job, provider))
        );

        notificationService.notifyProvider(
            provider.getUser().getId().toString(),
            Map.of("type", "JOB_ACCEPTED_SUCCESS", "jobId", jobId, "customer", job.getCustomer().getFullName())
        );

        // Email: job accepted — notify customer
        String customerEmail = job.getCustomer().getEmail();
        if (customerEmail != null) {
            notificationService.sendEmail(
                customerEmail,
                "Job Accepted — #" + jobId.toString().substring(0, 8),
                "<p>Hi " + InputSanitizer.encodeForHtml(job.getCustomer().getFullName() != null ? job.getCustomer().getFullName() : "there") + ",</p>"
                    + "<p>A provider has accepted your job <strong>#" + jobId.toString().substring(0, 8) + "</strong>.</p>"
                    + "<p>You can track their progress in the app.</p>",
                "A provider has accepted your job #" + jobId.toString().substring(0, 8) + ". Track progress in the app."
            );
        }
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
    public Job completeJob(UUID jobId, UUID providerId, CompleteJobDTO dto) {
        Job job = validateProviderJob(jobId, providerId);

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Job must be in progress before completing");
        }

        job.setStatus(JobStatus.AWAITING_CUSTOMER_CONFIRMATION);
        job.setCompletionNotes(InputSanitizer.stripHtml(dto.getCompletionNotes()));
        job.setCompletionPhotos(dto.getCompletionPhotos() != null ? dto.getCompletionPhotos() : List.of());
        job.setCompletionLatitude(dto.getLatitude());
        job.setCompletionLongitude(dto.getLongitude());
        job.setConfirmationDeadline(LocalDateTime.now().plusHours(72));
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job {} marked complete by provider {}. Awaiting customer confirmation. Auto-confirm deadline: {}",
            jobId, providerId, job.getConfirmationDeadline());

        notificationService.notifyCustomer(
            job.getCustomer().getId().toString(),
            Map.of("type", "JOB_AWAITING_CONFIRMATION", "jobId", jobId,
                   "completionNotes", job.getCompletionNotes() != null ? job.getCompletionNotes() : "",
                   "photos", job.getCompletionPhotos())
        );

        return job;
    }

    @Transactional
    public void confirmCompletion(UUID jobId, UUID customerId) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.AWAITING_CUSTOMER_CONFIRMATION) {
            throw new IllegalStateException("Job is not awaiting customer confirmation");
        }

        if (!job.getCustomer().getId().equals(customerId)) {
            throw new SecurityException("Only the customer can confirm completion");
        }

        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        Provider provider = job.getProvider();
        if (provider != null) {
            provider.setJobsCompleted(provider.getJobsCompleted() + 1);
            providerRepository.save(provider);
        }

        // Release escrow now — customer confirmed
        if (provider != null) {
            try {
                paymentRepository.findByJobId(jobId).stream()
                    .filter(p -> p.getStatus() == com.theguy.app.enums.PaymentStatus.HELD)
                    .findFirst()
                    .ifPresent(payment -> paymentGatewayService.releaseEscrowOnJobCompletion(
                        jobId, provider.getId(), payment.getId()));
            } catch (Exception e) {
                log.error("Failed to release escrow for job {}: {}", jobId, e.getMessage());
            }
        }

        log.info("Customer {} confirmed completion of job {}", customerId, jobId);

        notificationService.notifyCustomer(
            customerId.toString(),
            Map.of("type", "JOB_COMPLETED", "jobId", jobId)
        );
        if (provider != null) {
            notificationService.notifyProvider(
                provider.getUser().getId().toString(),
                Map.of("type", "JOB_PAYMENT_RELEASED", "jobId", jobId)
            );

            // Email: payment released to provider
            String providerEmail = provider.getUser().getEmail();
            if (providerEmail != null) {
                notificationService.sendEmail(
                    providerEmail,
                    "Payment Released — Job #" + jobId.toString().substring(0, 8),
                    "<p>Hi " + InputSanitizer.encodeForHtml(provider.getUser().getFullName() != null ? provider.getUser().getFullName() : "there") + ",</p>"
                        + "<p>Your earnings for job <strong>#" + jobId.toString().substring(0, 8) + "</strong> have been released to your wallet.</p>"
                        + "<p>Log in to view your balance and request a payout.</p>",
                    "Your earnings for job #" + jobId.toString().substring(0, 8) + " have been released to your wallet."
                );
            }
        }

        // Email: job completed to customer
        String customerEmail = job.getCustomer().getEmail();
        if (customerEmail != null) {
            notificationService.sendEmail(
                customerEmail,
                "Job Completed — #" + jobId.toString().substring(0, 8),
                "<p>Hi " + InputSanitizer.encodeForHtml(job.getCustomer().getFullName() != null ? job.getCustomer().getFullName() : "there") + ",</p>"
                    + "<p>Your job <strong>#" + jobId.toString().substring(0, 8) + "</strong> has been completed.</p>"
                    + "<p>Payment has been released to the provider. Thank you for using The Guy!</p>",
                "Your job #" + jobId.toString().substring(0, 8) + " has been completed. Payment released."
            );
        }
    }

    @Transactional
    public void rejectCompletion(UUID jobId, UUID customerId, RejectCompletionDTO dto) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.AWAITING_CUSTOMER_CONFIRMATION) {
            throw new IllegalStateException("Job is not awaiting customer confirmation");
        }

        if (!job.getCustomer().getId().equals(customerId)) {
            throw new SecurityException("Only the customer can reject completion");
        }

        // Prevent duplicate disputes
        if (disputeRepository.findByJobId(jobId).isPresent()) {
            throw new IllegalStateException("A dispute already exists for this job");
        }

        job.setStatus(JobStatus.DISPUTED);
        jobRepository.save(job);

        // Create dispute record
        Dispute dispute = Dispute.builder()
            .job(job)
            .openedBy(job.getCustomer())
            .reason(dto.getReason())
            .status(DisputeStatus.OPEN)
            .build();
        disputeRepository.save(dispute);

        log.info("Customer {} rejected completion of job {}. Dispute opened: {}", customerId, jobId, dto.getReason());

        notificationService.notifyBothParties(job, Map.of(
            "type", "JOB_DISPUTED", "jobId", jobId,
            "reason", dto.getReason()
        ));
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

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.DISPUTED) {
            throw new IllegalStateException("Cannot cancel a " + job.getStatus().getDisplayName() + " job");
        }

        JobStatus previousStatus = job.getStatus();
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

        // Refund escrow if payment was held and job progressed past matching
        if (previousStatus != JobStatus.REQUESTED && previousStatus != JobStatus.MATCHING) {
            try {
                paymentRepository.findByJobId(jobId).stream()
                    .filter(p -> p.getStatus() == com.theguy.app.enums.PaymentStatus.HELD)
                    .findFirst()
                    .ifPresent(payment -> paymentGatewayService.refundEscrowOnCancellation(jobId, payment.getId()));
            } catch (Exception e) {
                log.error("Failed to refund escrow for cancelled job {}: {}", jobId, e.getMessage());
            }
        }

        if (job.getProvider() != null && previousStatus == JobStatus.ASSIGNED) {
            Provider provider = job.getProvider();
            provider.setJobsCancelled(provider.getJobsCancelled() + 1);
            providerRepository.save(provider);
        }

        log.info("Job {} cancelled by {} with reason: {}", jobId, role, reason);

        notificationService.notifyBothParties(job, Map.of("type", "JOB_CANCELLED", "reason", reason));
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

    /**
     * Provider-driven status transition restricted to in-journey states.
     * Validates that the provider is the one assigned to the job.
     */
    @Transactional
    public void updateProviderStatus(UUID jobId, UUID providerId, JobStatus status) {
        Job job = validateProviderJob(jobId, providerId);

        if (status != JobStatus.ON_THE_WAY
                && status != JobStatus.ARRIVED
                && status != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Provider can only update status to ON_THE_WAY, ARRIVED, or IN_PROGRESS");
        }

        job.setStatus(status);
        jobRepository.save(job);

        log.info("Job {} status updated by provider {} to {}", jobId, providerId, status);
    }

    /**
     * Scheduled task: auto-confirms jobs where the customer hasn't responded
     * within 72 hours of the provider marking completion.
     */
    @Transactional
    @Scheduled(fixedRate = 3600000) // Every hour
    public void autoConfirmStaleJobs() {
        List<Job> staleJobs = jobRepository.findByStatusAndConfirmationDeadlineBefore(
            JobStatus.AWAITING_CUSTOMER_CONFIRMATION, LocalDateTime.now());

        for (Job job : staleJobs) {
            try {
                log.info("Auto-confirming job {} — customer did not respond by deadline {}", job.getId(), job.getConfirmationDeadline());

                job.setStatus(JobStatus.COMPLETED);
                jobRepository.save(job);

                Provider provider = job.getProvider();
                if (provider != null) {
                    provider.setJobsCompleted(provider.getJobsCompleted() + 1);
                    providerRepository.save(provider);

                    paymentRepository.findByJobId(job.getId()).stream()
                        .filter(p -> p.getStatus() == com.theguy.app.enums.PaymentStatus.HELD)
                        .findFirst()
                        .ifPresent(payment -> paymentGatewayService.releaseEscrowOnJobCompletion(
                            job.getId(), provider.getId(), payment.getId()));
                }

                notificationService.notifyBothParties(job, Map.of(
                    "type", "JOB_AUTO_CONFIRMED", "jobId", job.getId()
                ));
            } catch (Exception e) {
                log.error("Failed to auto-confirm job {}: {}", job.getId(), e.getMessage());
            }
        }

        if (!staleJobs.isEmpty()) {
            log.info("Auto-confirmed {} stale jobs", staleJobs.size());
        }
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
    public List<NearbyJobDTO> getNearbyJobs(double lat, double lng, double radiusMeters) {
        com.theguy.app.utils.LocationUtils.BoundingBox bbox =
            com.theguy.app.utils.LocationUtils.getBoundingBox(lat, lng, radiusMeters);

        return jobRepository.findOpenJobsNear(
                bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng).stream()
            .map(job -> mapToNearbyJobDTO(job, lat, lng))
            .sorted(java.util.Comparator.comparingDouble(NearbyJobDTO::getDistance))
            .collect(Collectors.toList());
    }

    private NearbyJobDTO mapToNearbyJobDTO(Job job, double lat, double lng) {
        double distanceKm = 0.0;
        if (job.getLatitude() != null && job.getLongitude() != null) {
            distanceKm = com.theguy.app.utils.LocationUtils.calculateDistance(
                lat, lng, job.getLatitude(), job.getLongitude()) / 1000.0;
        }

        double price = 0.0;
        if (job.getPriceEstimateMin() != null && job.getPriceEstimateMax() != null) {
            price = (job.getPriceEstimateMin() + job.getPriceEstimateMax()) / 2;
        } else if (job.getPriceEstimateMin() != null) {
            price = job.getPriceEstimateMin();
        }

        String customerName = job.getCustomer() != null && job.getCustomer().getFullName() != null
            ? job.getCustomer().getFullName() : "Customer";
        String customerPhone = job.getCustomer() != null && job.getCustomer().getPhoneNumber() != null
            ? job.getCustomer().getPhoneNumber() : "";
        String customerId = job.getCustomer() != null
            ? job.getCustomer().getId().toString() : "";

        return NearbyJobDTO.builder()
            .id(job.getId())
            .customerId(customerId)
            .customerName(customerName)
            .customerPhone(customerPhone)
            .category(job.getServiceCategory())
            .description(job.getDescription())
            .distance(distanceKm)
            .price(price)
            .status(job.getStatus().name())
            .urgency(job.getUrgency() != null ? job.getUrgency().name() : "SCHEDULED")
            .requestedAt(job.getCreatedAt() != null ? job.getCreatedAt().toString() : null)
            .pickupLat(job.getLatitude() != null ? job.getLatitude() : 0.0)
            .pickupLng(job.getLongitude() != null ? job.getLongitude() : 0.0)
            .hasResponded(false)
            .build();
    }

    @Transactional(readOnly = true)
    public List<JobResponseDTO> getHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Job> jobs = jobRepository.findByCustomerId(user.getId());
        // Provider jobs are keyed by provider id, not user id
        providerRepository.findByUserId(user.getId())
            .ifPresent(p -> jobs.addAll(jobRepository.findByProviderId(p.getId())));

        return jobs.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void acceptJob(UUID jobId) {
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

        if (job.getStatus() != JobStatus.MATCHING
                && job.getStatus() != JobStatus.REQUESTED
                && job.getStatus() != JobStatus.ASSIGNED) {
            throw new IllegalStateException("Job must be matching, requested, or assigned before declining");
        }

        JobStatus previousStatus = job.getStatus();
        job.setStatus(JobStatus.REQUESTED);
        job.setProvider(null);
        job.setAcceptedAt(null);
        jobRepository.save(job);

        log.info("Job {} declined, returned to available pool", jobId);

        // Re-dispatch to other nearby providers (broadcast fallback). Providers who
        // already declined are excluded, so a targeted request naturally falls back
        // to the next best available provider.
        if (previousStatus == JobStatus.MATCHING || previousStatus == JobStatus.REQUESTED) {
            matchingService.startMatching(job, null);
        }
    }

    /**
     * Provider-initiated decline that validates the caller was actually
     * offered (or assigned to) the job before it is returned to the pool.
     */
    @Transactional
    public void declineJob(UUID jobId, UUID providerId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getStatus() != JobStatus.MATCHING
                && job.getStatus() != JobStatus.REQUESTED
                && job.getStatus() != JobStatus.ASSIGNED) {
            throw new IllegalStateException("Job must be matching, requested, or assigned before declining");
        }

        boolean ownsJob = job.getProvider() != null && job.getProvider().getId().equals(providerId);
        boolean wasOffered = jobRequestRepository.findByJobId(jobId).stream()
            .anyMatch(r -> r.getProviderId().equals(providerId)
                && (r.getStatus() == JobRequestStatus.PENDING || r.getStatus() == JobRequestStatus.EXPIRED));

        if (!ownsJob && !wasOffered) {
            throw new SecurityException("Not authorized to decline this job");
        }

        declineJob(jobId);
    }

    private void expirePendingRequests(UUID jobId, UUID winningProviderId) {
        for (JobRequest request : jobRequestRepository.findByJobId(jobId)) {
            if (request.getStatus() == JobRequestStatus.PENDING
                    && !request.getProviderId().equals(winningProviderId)) {
                request.setStatus(JobRequestStatus.EXPIRED);
                request.setRespondedAt(LocalDateTime.now());
                jobRequestRepository.save(request);
            }
        }
    }

    /** Provider summary for the customer-facing JOB_ACCEPTED notification. */
    private Map<String, Object> buildProviderSummary(Job job, Provider provider) {
        double sqs = providerStatisticsService.getStatistics(provider.getId())
            .map(com.theguy.app.entity.ProviderStatistics::getSqs)
            .orElse(0.0);

        double distanceKm = 0.0;
        if (job.getLatitude() != null && job.getLongitude() != null) {
            distanceKm = locationRepository.findByProviderId(provider.getId())
                .map(pl -> com.theguy.app.utils.LocationUtils.calculateDistance(
                    pl.getLatitude(), pl.getLongitude(),
                    job.getLatitude(), job.getLongitude()) / 1000.0)
                .orElse(0.0);
        }

        double price = job.getFinalPrice() != null ? job.getFinalPrice()
            : (job.getPriceEstimateMin() != null ? job.getPriceEstimateMin() : 0.0);

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", provider.getId());
        summary.put("name", provider.getUser() != null ? provider.getUser().getFullName() : "Provider");
        summary.put("avatar", provider.getProfileImageUrl());
        summary.put("rating", provider.getRatingAvg());
        summary.put("reviews", provider.getTotalReviews());
        summary.put("serviceQualityScore", sqs);
        summary.put("distance", distanceKm);
        summary.put("price", price);
        return summary;
    }

    @Transactional(readOnly = true)
    public JobResponseDTO getJobDetails(UUID jobId, UUID userId, String role) {
        Job job = jobRepository.findByIdWithDetails(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        boolean isAuthorized = false;
        if (job.getCustomer().getId().equals(userId)) isAuthorized = true;
        if (job.getProvider() != null
                && (job.getProvider().getId().equals(userId)
                    || job.getProvider().getUser().getId().equals(userId))) isAuthorized = true;
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

    @Transactional(readOnly = true)
    public List<Job> getJobsAwaitingConfirmationForCustomer(UUID customerId) {
        return jobRepository.findByCustomerIdAndStatus(customerId, JobStatus.AWAITING_CUSTOMER_CONFIRMATION);
    }

    @Transactional(readOnly = true)
    public long getJobsAwaitingConfirmationCount(UUID providerId) {
        return jobRepository.countByProviderIdAndStatus(providerId, JobStatus.AWAITING_CUSTOMER_CONFIRMATION);
    }

    public JobResponseDTO toResponseDTO(Job job) {
        return mapToResponseDTO(job);
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
                    .fullName(job.getProvider().getUser() != null
                        ? job.getProvider().getUser().getFullName() : null)
                    .rating(job.getProvider().getRatingAvg())
                    .jobsCompleted(job.getProvider().getJobsCompleted())
                    .verificationLevel(job.getProvider().getVerificationLevel() != null
                        ? job.getProvider().getVerificationLevel().name() : null)
                    .build() : null)
            .createdAt(job.getCreatedAt())
            .acceptedAt(job.getAcceptedAt())
            .completedAt(job.getCompletedAt())
            .completionNotes(job.getCompletionNotes())
            .completionPhotos(job.getCompletionPhotos())
            .completionLatitude(job.getCompletionLatitude())
            .completionLongitude(job.getCompletionLongitude())
            .confirmationDeadline(job.getConfirmationDeadline())
            .build();
    }
}