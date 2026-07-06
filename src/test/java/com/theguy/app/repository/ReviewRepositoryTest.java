package com.theguy.app.repository;

import com.theguy.app.entity.Review;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void shouldSaveAndFindReview() {
        UUID providerId = UUID.randomUUID();
        Review review = new Review();
        review.setJobId(UUID.randomUUID());
        review.setCustomerId(UUID.randomUUID());
        review.setProviderId(providerId);
        review.setRatingQuality(4);
        review.setRatingReliability(5);
        review.setRatingCommunication(4);
        review.setComment("Great service");

        Review saved = reviewRepository.save(review);
        assertThat(saved.getId()).isNotNull();

        Page<Review> found = reviewRepository.findByProviderId(providerId, PageRequest.of(0, 10));
        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getComment()).isEqualTo("Great service");
        assertThat(found.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnAverageRatings() {
        UUID providerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();

        Review r1 = new Review();
        r1.setJobId(jobId1);
        r1.setCustomerId(customerId);
        r1.setProviderId(providerId);
        r1.setRatingQuality(5);
        r1.setRatingReliability(5);
        r1.setRatingCommunication(5);
        r1.setComment("Excellent");
        reviewRepository.save(r1);

        Review r2 = new Review();
        r2.setJobId(jobId2);
        r2.setCustomerId(customerId);
        r2.setProviderId(providerId);
        r2.setRatingQuality(3);
        r2.setRatingReliability(3);
        r2.setRatingCommunication(3);
        r2.setComment("Average");
        reviewRepository.save(r2);

        Object result = reviewRepository.getAverageRatingsByProviderId(providerId);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnReviewCount() {
        UUID providerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            Review r = new Review();
            r.setJobId(UUID.randomUUID());
            r.setCustomerId(customerId);
            r.setProviderId(providerId);
            r.setRatingQuality(4);
            r.setRatingReliability(4);
            r.setRatingCommunication(4);
            r.setComment("Test review " + i);
            reviewRepository.save(r);
        }

        Long count = reviewRepository.getReviewCountByProviderId(providerId);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void shouldPaginateReviews() {
        UUID providerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        for (int i = 0; i < 25; i++) {
            Review r = new Review();
            r.setJobId(UUID.randomUUID());
            r.setCustomerId(customerId);
            r.setProviderId(providerId);
            r.setRatingQuality(4);
            r.setRatingReliability(4);
            r.setRatingCommunication(4);
            r.setComment("Review " + i);
            reviewRepository.save(r);
        }

        Page<Review> page1 = reviewRepository.findByProviderId(providerId, PageRequest.of(0, 10));
        assertThat(page1.getContent()).hasSize(10);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat(page1.getTotalElements()).isEqualTo(25);

        Page<Review> page2 = reviewRepository.findByProviderId(providerId, PageRequest.of(1, 10));
        assertThat(page2.getContent()).hasSize(10);

        Page<Review> page3 = reviewRepository.findByProviderId(providerId, PageRequest.of(2, 10));
        assertThat(page3.getContent()).hasSize(5);
    }
}
