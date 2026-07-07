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
        Review review = Review.builder()
            .jobId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .providerId(providerId)
            .overallExperience(85)
            .timeliness(90)
            .professionalism(88)
            .communication(92)
            .courtesy(95)
            .workQuality(87)
            .attentionToDetail(90)
            .cleanliness(93)
            .reliability(89)
            .valueForMoney(86)
            .recommendation(95)
            .serviceQualityScore(90.0)
            .comment("Great service")
            .helpfulCount(0)
            .build();

        Review saved = reviewRepository.save(review);
        assertThat(saved.getId()).isNotNull();

        Page<Review> found = reviewRepository.findByProviderId(providerId, PageRequest.of(0, 10));
        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getComment()).isEqualTo("Great service");
        assertThat(found.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnAverageSqs() {
        UUID providerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();

        Review r1 = Review.builder()
            .jobId(jobId1)
            .customerId(customerId)
            .providerId(providerId)
            .overallExperience(95)
            .timeliness(90)
            .professionalism(92)
            .communication(88)
            .courtesy(95)
            .workQuality(93)
            .attentionToDetail(90)
            .cleanliness(92)
            .reliability(94)
            .valueForMoney(91)
            .recommendation(95)
            .serviceQualityScore(92.5)
            .comment("Excellent")
            .helpfulCount(0)
            .build();
        reviewRepository.save(r1);

        Review r2 = Review.builder()
            .jobId(jobId2)
            .customerId(customerId)
            .providerId(providerId)
            .overallExperience(75)
            .timeliness(70)
            .professionalism(72)
            .communication(78)
            .courtesy(75)
            .workQuality(73)
            .attentionToDetail(70)
            .cleanliness(72)
            .reliability(74)
            .valueForMoney(71)
            .recommendation(75)
            .serviceQualityScore(73.0)
            .comment("Average")
            .helpfulCount(0)
            .build();
        reviewRepository.save(r2);

        Double avgSqs = reviewRepository.getAverageSqsByProviderId(providerId);
        assertThat(avgSqs).isNotNull();
        assertThat(avgSqs).isEqualTo(82.75); // (92.5 + 73.0) / 2
    }

    @Test
    void shouldReturnReviewCount() {
        UUID providerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            Review r = Review.builder()
                .jobId(UUID.randomUUID())
                .customerId(customerId)
                .providerId(providerId)
                .overallExperience(85)
                .timeliness(80)
                .professionalism(82)
                .communication(84)
                .courtesy(85)
                .workQuality(83)
                .attentionToDetail(80)
                .cleanliness(82)
                .reliability(84)
                .valueForMoney(81)
                .recommendation(85)
                .serviceQualityScore(83.0)
                .comment("Test review " + i)
                .helpfulCount(0)
                .build();
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
            Review r = Review.builder()
                .jobId(UUID.randomUUID())
                .customerId(customerId)
                .providerId(providerId)
                .overallExperience(85)
                .timeliness(80)
                .professionalism(82)
                .communication(84)
                .courtesy(85)
                .workQuality(83)
                .attentionToDetail(80)
                .cleanliness(82)
                .reliability(84)
                .valueForMoney(81)
                .recommendation(85)
                .serviceQualityScore(83.0)
                .comment("Review " + i)
                .helpfulCount(0)
                .build();
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