package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ReviewRepository;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformStatsController {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final JobRepository jobRepository;
    private final ReviewRepository reviewRepository;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("totalProviders", providerRepository.count());
            stats.put("totalUsers", userRepository.count());
            stats.put("totalJobs", jobRepository.count());
            long totalReviews = reviewRepository.count();
            stats.put("totalReviews", totalReviews);
        } catch (Exception e) {
            stats.put("totalProviders", 0L);
            stats.put("totalUsers", 0L);
            stats.put("totalJobs", 0L);
            stats.put("totalReviews", 0L);
        }
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
