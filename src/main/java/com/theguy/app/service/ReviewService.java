package com.theguy.app.service;

import com.theguy.app.dto.ReviewDTO;
import com.theguy.app.dto.ReviewSummaryDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.utils.InputSanitizer;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            .comment(InputSanitizer.stripHtml(dto.getComment()))
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

    @Transactional(readOnly = true)
    public ReviewSummaryDTO getReviewSummary(UUID providerId) {
        var statsOpt = providerStatisticsRepository.findById(providerId);

        double overallSqs = 0.0;
        long totalReviews = 0;
        Map<String, Double> categories = new LinkedHashMap<>();

        if (statsOpt.isPresent()) {
            ProviderStatistics stats = statsOpt.get();
            overallSqs = stats.getSqs();
            totalReviews = stats.getReviewCount();
            categories.put("professionalism", stats.getProfessionalismScore());
            categories.put("communication", stats.getCommunicationScore());
            categories.put("timeliness", stats.getTimelinessScore());
            categories.put("workQuality", stats.getWorkQualityScore());
            categories.put("reliability", stats.getReliabilityScore());
            categories.put("courtesy", stats.getCourtesyScore());
            categories.put("valueForMoney", stats.getValueScore());
        } else {
            Double avgSqs = reviewRepository.getAverageSqsByProviderId(providerId);
            Long count = reviewRepository.getReviewCountByProviderId(providerId);
            overallSqs = avgSqs != null ? avgSqs : 0.0;
            totalReviews = count != null ? count : 0L;
        }

        return ReviewSummaryDTO.builder()
                .overallSqs(overallSqs)
                .totalReviews(totalReviews)
                .categories(categories)
                .recentTrend(computeRecentTrend(providerId))
                .build();
    }

    private String computeRecentTrend(UUID providerId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sixtyDaysAgo = LocalDateTime.now().minusDays(60);

        List<Review> recentReviews = reviewRepository.findByProviderId(providerId,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent();

        double recentAvg = recentReviews.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(thirtyDaysAgo))
                .mapToDouble(r -> r.getServiceQualityScore() != null ? r.getServiceQualityScore() : 0.0)
                .average().orElse(0.0);

        double olderAvg = recentReviews.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sixtyDaysAgo) && r.getCreatedAt().isBefore(thirtyDaysAgo))
                .mapToDouble(r -> r.getServiceQualityScore() != null ? r.getServiceQualityScore() : 0.0)
                .average().orElse(0.0);

        if (recentAvg == 0 && olderAvg == 0) return "no_data";
        if (olderAvg == 0) return "improving";
        double diff = recentAvg - olderAvg;
        if (diff > 2) return "improving";
        if (diff < -2) return "declining";
        return "stable";
    }
    
    public record RatingSummary(double averageRating, long totalReviews) {}
}