package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.CompleteJobDTO;
import com.theguy.app.dto.JobRequestDTO;
import com.theguy.app.dto.JobResponseDTO;
import com.theguy.app.dto.NearbyJobDTO;
import com.theguy.app.dto.RejectCompletionDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.User;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    
    @PostMapping("/request")
    public ResponseEntity<?> requestJob(@Valid @RequestBody JobRequestDTO dto, Authentication authentication) {
        User customer = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Job job = jobService.requestJob(dto, customer);
        JobResponseDTO response = JobResponseDTO.builder()
            .id(job.getId())
            .serviceCategory(job.getServiceCategory())
            .description(job.getDescription())
            .status(job.getStatus())
            .urgency(job.getUrgency())
            .priceEstimateMin(job.getPriceEstimateMin())
            .priceEstimateMax(job.getPriceEstimateMax())
            .createdAt(job.getCreatedAt())
            .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/{jobId}/accept")
    public ResponseEntity<?> acceptJob(@PathVariable UUID jobId, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        jobService.acceptJob(jobId, provider.getId());
        return ResponseEntity.ok(ApiResponse.success("Job accepted", null));
    }

    @GetMapping
    public List<JobResponseDTO> getJobs() {
        return jobService.getAllJobs();
    }

    @GetMapping("/nearby")
    public List<NearbyJobDTO> nearbyJobs(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "20000") double radius) {
        return jobService.getNearbyJobs(lat, lng, radius);
    }

    @GetMapping("/history")
    public List<JobResponseDTO> history(Authentication auth) {
        return jobService.getHistory(auth.getName());
    }

    @PostMapping("/{jobId}/decline")
    public ResponseEntity<?> declineJob(@PathVariable UUID jobId) {
        jobService.declineJob(jobId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{jobId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable UUID jobId,
            @RequestParam JobStatus status) {
        jobService.updateStatus(jobId, status);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{jobId}/complete")
    public ResponseEntity<?> complete(@PathVariable UUID jobId,
                                       @Valid @RequestBody CompleteJobDTO dto,
                                       Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        jobService.completeJob(jobId, provider.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Job marked complete, awaiting customer confirmation", null));
    }

    @PostMapping("/{jobId}/confirm-completion")
    public ResponseEntity<?> confirmCompletion(@PathVariable UUID jobId, Authentication authentication) {
        User customer = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        jobService.confirmCompletion(jobId, customer.getId());
        return ResponseEntity.ok(ApiResponse.success("Completion confirmed, escrow released", null));
    }

    @PostMapping("/{jobId}/reject-completion")
    public ResponseEntity<?> rejectCompletion(@PathVariable UUID jobId,
                                               @Valid @RequestBody RejectCompletionDTO dto,
                                               Authentication authentication) {
        User customer = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        jobService.rejectCompletion(jobId, customer.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Completion rejected, dispute opened", null));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getCustomerStats(Authentication authentication) {
        User customer = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<Job> jobs = jobService.getJobsByCustomer(customer.getId());
        long totalJobs = jobs.size();
        long completedJobs = jobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
        long activeJobs = jobs.stream().filter(j ->
            j.getStatus() == JobStatus.IN_PROGRESS || j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.MATCHING
        ).count();

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalJobs", totalJobs);
        stats.put("completedJobs", completedJobs);
        stats.put("activeJobs", activeJobs);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
