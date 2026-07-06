package com.theguy.app.service;

import com.theguy.app.dto.ReviewDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.Review;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ReviewRepository;
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

    @Transactional
    public Review createReview(ReviewDTO dto, UUID customerId) {
        Job job = jobRepository.findById(dto.getJobId())
            .orElseThrow(() -> new RuntimeException("Job not found"));

        UUID providerId = dto.getProviderId() != null ? dto.getProviderId() : job.getProvider().getId();
        Review review = new Review();
        review.setJobId(dto.getJobId());
        review.setCustomerId(customerId);
        review.setProviderId(providerId);
        review.setRatingQuality(dto.getRatingQuality());
        review.setRatingReliability(dto.getRatingReliability());
        review.setRatingCommunication(dto.getRatingCommunication());
        review.setComment(dto.getComment());

        Review saved = reviewRepository.save(review);
        log.info("Review created: {} for job: {}", saved.getId(), dto.getJobId());
        return saved;
    }

    @Transactional(readOnly = true)
    public RatingSummary getProviderRatingSummary(UUID providerId) {
        Double avgQuality = reviewRepository.getAverageOverallRating(providerId);
        Long totalReviews = reviewRepository.getReviewCountByProviderId(providerId);
        return new RatingSummary(avgQuality != null ? avgQuality : 0.0, totalReviews != null ? totalReviews : 0L);
    }

    @Transactional
    public void markReviewHelpful(UUID reviewId) {
        reviewRepository.incrementHelpfulCount(reviewId);
    }

    public record RatingSummary(double averageRating, long totalReviews) {}
}
