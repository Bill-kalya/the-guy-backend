package com.theguy.app.controller;

import com.theguy.app.dto.JobRequestDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.User;
import com.theguy.app.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    
    @PostMapping("/request")
    public ResponseEntity<?> requestJob(@RequestBody JobRequestDTO dto) {
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        User customer = userService.findById(UUID.fromString(userId));
        
        Job job = jobService.requestJob(dto, customer);
        return ResponseEntity.ok(Map.of(
            "jobId", job.getId(),
            "status", job.getStatus(),
            "estimateMin", job.getPriceEstimateMin(),
            "estimateMax", job.getPriceEstimateMax()
        ));
    }
    
    @PostMapping("/{jobId}/accept")
    public ResponseEntity<?> acceptJob(@PathVariable UUID jobId) {
        String providerId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        jobService.acceptJob(jobId, UUID.fromString(providerId));
        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }
}