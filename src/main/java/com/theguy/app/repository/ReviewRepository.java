package com.theguy.app.repository;

import com.theguy.app.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    
    /**
     * Find review by job ID (each job can have only one review)
     */
    Optional<Review> findByJobId(UUID jobId);
    
    /**
     * Check if a job has already been reviewed
     */
    boolean existsByJobId(UUID jobId);
    
    /**
     * Find all reviews for a specific provider with pagination
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId ORDER BY r.createdAt DESC")
    org.springframework.data.domain.Page<Review> findByProviderId(@Param("providerId") UUID providerId, 
                                   org.springframework.data.domain.Pageable pageable);
    
    /**
     * Get average SQS for a provider
     */
    @Query("SELECT COALESCE(AVG(r.serviceQualityScore), 0) FROM Review r WHERE r.providerId = :providerId")
    Double getAverageSqsByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get average category scores for a provider
     */
    @Query("SELECT " +
           "COALESCE(AVG(r.professionalism), 0) as avgProfessionalism, " +
           "COALESCE(AVG(r.communication), 0) as avgCommunication, " +
           "COALESCE(AVG(r.timeliness), 0) as avgTimeliness, " +
           "COALESCE(AVG(r.workQuality), 0) as avgWorkQuality, " +
           "COALESCE(AVG(r.reliability), 0) as avgReliability, " +
           "COALESCE(AVG(r.courtesy), 0) as avgCourtesy, " +
           "COALESCE(AVG(r.valueForMoney), 0) as avgValue, " +
           "COUNT(r) as totalReviews " +
           "FROM Review r WHERE r.providerId = :providerId")
    Object[] getCategoryAveragesByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get recent reviews for a provider (last 30 days)
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId " +
           "AND r.createdAt >= :since " +
           "ORDER BY r.createdAt DESC")
    List<Review> findRecentReviewsByProviderId(@Param("providerId") UUID providerId,
                                               @Param("since") java.time.LocalDateTime since);
    
    /**
     * Get reviews by customer
     */
    @Query("SELECT r FROM Review r WHERE r.customerId = :customerId ORDER BY r.createdAt DESC")
    List<Review> findByCustomerId(@Param("customerId") UUID customerId, 
                                   org.springframework.data.domain.Pageable pageable);
    
    /**
     * Get total review count for a provider
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.providerId = :providerId")
    Long getReviewCountByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get top rated providers by SQS (minimum reviews threshold)
     */
    @Query("SELECT r.providerId, AVG(r.serviceQualityScore) as avgSqs, COUNT(r) as reviewCount " +
           "FROM Review r " +
           "GROUP BY r.providerId " +
           "HAVING COUNT(r) >= :minReviews " +
           "ORDER BY avgSqs DESC")
    List<Object[]> findTopRatedProvidersBySqs(@Param("minReviews") int minReviews, 
                                              org.springframework.data.domain.Pageable pageable);
    
    /**
     * Delete all reviews for a provider (admin use)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Review r WHERE r.providerId = :providerId")
    void deleteAllByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Check if customer has already reviewed this provider
     */
    @Query("SELECT COUNT(r) > 0 FROM Review r WHERE r.customerId = :customerId AND r.providerId = :providerId")
    boolean hasCustomerReviewedProvider(@Param("customerId") UUID customerId, 
                                        @Param("providerId") UUID providerId);
    
    /**
     * Get reviews with comments (text reviews)
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId AND LENGTH(r.comment) > 10 " +
           "ORDER BY r.createdAt DESC")
    List<Review> findReviewsWithCommentsByProviderId(@Param("providerId") UUID providerId,
                                                     org.springframework.data.domain.Pageable pageable);
    
    /**
     * Get monthly review trend for a provider
     */
    @Query(value = "SELECT DATE_TRUNC('month', created_at) as month, " +
           "COUNT(*) as review_count, " +
           "AVG(service_quality_score) as avg_sqs " +
           "FROM reviews " +
           "WHERE provider_id = :providerId " +
           "AND created_at >= :startDate " +
           "GROUP BY DATE_TRUNC('month', created_at) " +
           "ORDER BY month DESC", 
           nativeQuery = true)
    List<Object[]> getMonthlyReviewTrend(@Param("providerId") UUID providerId, 
                                         @Param("startDate") java.time.LocalDateTime startDate);
    
    /**
     * Get reviews by SQS range
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId " +
           "AND r.serviceQualityScore BETWEEN :minSqs AND :maxSqs")
    List<Review> findBySqsRange(@Param("providerId") UUID providerId,
                                @Param("minSqs") double minSqs,
                                @Param("maxSqs") double maxSqs);
    
    /**
     * Count reviews with comments (text reviews)
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.providerId = :providerId AND LENGTH(r.comment) > 10")
    Long countReviewsWithComments(@Param("providerId") UUID providerId);
    
    /**
     * Increment the helpful count for a review
     */
    @Modifying
    @Transactional
    @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount + 1 WHERE r.id = :reviewId")
    void incrementHelpfulCount(@Param("reviewId") UUID reviewId);
}
