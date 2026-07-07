package com.theguy.app.service;

import com.theguy.app.dto.ReviewDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.entity.Review;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderStatisticsRepository;
import com.theguy.app.repository.ReviewRepository;
import com.theguy.app.util.SqsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    
    private final ReviewRepository reviewRepository;
    private final JobRepository jobRepository;
    private final SqsCalculator sqsCalculator;
    private final ProviderStatisticsService providerStatisticsService;
    private final ProviderStatisticsRepository providerStatisticsRepository;
    
    @Transactional
    public Review createReview(ReviewDTO dto, UUID customerId) {
        Job job = jobRepository.findById(dto.getJobId())
            .orElseThrow(() -> new RuntimeException("Job not found"));
        
        UUID providerId = dto.getProviderId() != null ? dto.getProviderId() : job.getProvider().getId();
        
        // Calculate SQS
        double sqs = sqsCalculator.calculate(dto);
        
        // Build review with SQS fields
        Review review = Review.builder()
            .jobId(dto.getJobId())
            .customerId(customerId)
            .providerId(providerId)
            .overallExperience(dto.getOverallExperience())
            .timeliness(dto.getTimeliness())
            .professionalism(dto.getProfessionalism())
            .communication(dto.getCommunication())
            .courtesy(dto.getCourtesy())
            .workQuality(dto.getWorkQuality())
            .attentionToDetail(dto.getAttentionToDetail())
            .cleanliness(dto.getCleanliness())
            .reliability(dto.getReliability())
            .valueForMoney(dto.getValueForMoney())
            .problemResolution(dto.getProblemResolution())
            .recommendation(dto.getRecommendation())
            .serviceQualityScore(sqs)
            .comment(dto.getComment())
            .build();
        
        Review saved = reviewRepository.save(review);
        log.info("Review created: {} for job: {} with SQS: {}", saved.getId(), dto.getJobId(), sqs);
        
        // Recalculate provider statistics
        providerStatisticsService.recalculate(providerId);
        
        return saved;
    }
    
    @Transactional(readOnly = true)
    public RatingSummary getProviderRatingSummary(UUID providerId) {
        // Try to get from cached statistics first
        var statsOpt = providerStatisticsRepository.findById(providerId);
        
        if (statsOpt.isPresent()) {
            ProviderStatistics stats = statsOpt.get();
            return new RatingSummary(stats.getSqs(), stats.getReviewCount());
        }
        
        // Fallback to calculating from reviews
        Double avgSqs = reviewRepository.getAverageSqsByProviderId(providerId);
        Long totalReviews = reviewRepository.getReviewCountByProviderId(providerId);
        return new RatingSummary(avgSqs != null ? avgSqs : 0.0, totalReviews != null ? totalReviews : 0L);
    }
    
    @Transactional
    public void markReviewHelpful(UUID reviewId) {
        reviewRepository.incrementHelpfulCount(reviewId);
    }
    
    public record RatingSummary(double averageRating, long totalReviews) {}
}