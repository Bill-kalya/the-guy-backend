package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.ReviewDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.Review;
import com.theguy.app.entity.User;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ReviewRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    
    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Review>> submitReview(@Valid @RequestBody ReviewDTO dto) {
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        
        User customer = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify customer completed the job
        Job job = jobRepository.findById(dto.getJobId())
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        if (!job.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException("You can only review your own jobs");
        }
        
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Can only review completed jobs");
        }
        
        Review review = reviewService.createReview(dto, customer.getId());
        return ResponseEntity.ok(ApiResponse.success("Review submitted successfully", review));
    }
    
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<List<Review>>> getProviderReviews(
            @PathVariable UUID providerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Review> reviews = reviewRepository.findByProviderId(providerId, page, size);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }
    
    @GetMapping("/provider/{providerId}/average")
    public ResponseEntity<ApiResponse<ReviewService.RatingSummary>> getProviderRating(@PathVariable UUID providerId) {
        ReviewService.RatingSummary summary = reviewService.getProviderRatingSummary(providerId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
    
    @PutMapping("/{reviewId}/helpful")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> markHelpful(@PathVariable UUID reviewId) {
        reviewService.markReviewHelpful(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review marked as helpful", null));
    }
}