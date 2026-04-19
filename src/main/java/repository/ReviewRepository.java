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
    List<Review> findByProviderId(@Param("providerId") UUID providerId, 
                                   org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find all reviews for a specific provider (simplified version with limit)
     */
    @Query(value = "SELECT * FROM reviews WHERE provider_id = :providerId ORDER BY created_at DESC LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<Review> findByProviderId(@Param("providerId") UUID providerId, 
                                   @Param("offset") int offset, 
                                   @Param("limit") int limit);
    
    /**
     * Get average ratings for a provider
     */
    @Query("SELECT AVG(r.ratingQuality) as avgQuality, " +
           "AVG(r.ratingReliability) as avgReliability, " +
           "AVG(r.ratingCommunication) as avgCommunication, " +
           "COUNT(r) as totalReviews " +
           "FROM Review r WHERE r.providerId = :providerId")
    Object[] getAverageRatingsByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get rating distribution for a provider
     */
    @Query("SELECT FLOOR((r.ratingQuality + r.ratingReliability + r.ratingCommunication) / 3) as starRating, " +
           "COUNT(r) as count " +
           "FROM Review r " +
           "WHERE r.providerId = :providerId " +
           "GROUP BY FLOOR((r.ratingQuality + r.ratingReliability + r.ratingCommunication) / 3) " +
           "ORDER BY starRating DESC")
    List<Object[]> getRatingDistribution(@Param("providerId") UUID providerId);
    
    /**
     * Get recent reviews for a provider (last 30 days)
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId " +
           "AND r.createdAt >= CURRENT_TIMESTAMP - 30 DAYS " +
           "ORDER BY r.createdAt DESC")
    List<Review> findRecentReviewsByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get reviews by customer
     */
    @Query("SELECT r FROM Review r WHERE r.customerId = :customerId ORDER BY r.createdAt DESC")
    List<Review> findByCustomerId(@Param("customerId") UUID customerId, 
                                   org.springframework.data.domain.Pageable pageable);
    
    /**
     * Count helpful reviews for a provider
     */
    @Query("SELECT SUM(r.helpfulCount) FROM Review r WHERE r.providerId = :providerId")
    Integer getTotalHelpfulCount(@Param("providerId") UUID providerId);
    
    /**
     * Get average rating for a provider (simplified)
     */
    @Query("SELECT COALESCE(AVG((r.ratingQuality + r.ratingReliability + r.ratingCommunication) / 3.0), 0) " +
           "FROM Review r WHERE r.providerId = :providerId")
    Double getAverageOverallRating(@Param("providerId") UUID providerId);
    
    /**
     * Get total review count for a provider
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.providerId = :providerId")
    Long getReviewCountByProviderId(@Param("providerId") UUID providerId);
    
    /**
     * Get top rated providers (minimum reviews threshold)
     */
    @Query("SELECT r.providerId, AVG((r.ratingQuality + r.ratingReliability + r.ratingCommunication) / 3.0) as avgRating, " +
           "COUNT(r) as reviewCount " +
           "FROM Review r " +
           "GROUP BY r.providerId " +
           "HAVING COUNT(r) >= :minReviews " +
           "ORDER BY avgRating DESC")
    List<Object[]> findTopRatedProviders(@Param("minReviews") int minReviews, 
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
     * Get reviews with verified purchase only
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId AND r.isVerifiedPurchase = true " +
           "ORDER BY r.createdAt DESC")
    List<Review> findVerifiedReviewsByProviderId(@Param("providerId") UUID providerId,
                                                  org.springframework.data.domain.Pageable pageable);
    
    /**
     * Get reviews that need moderation (suspicious patterns)
     */
    @Query("SELECT r FROM Review r WHERE r.comment LIKE '%fake%' OR r.comment LIKE '%scam%' " +
           "OR r.ratingQuality = 1 OR r.ratingReliability = 1 OR r.ratingCommunication = 1 " +
           "ORDER BY r.createdAt DESC")
    List<Review> findSuspiciousReviews(org.springframework.data.domain.Pageable pageable);
    
    /**
     * Update review helpful count
     */
    @Modifying
    @Transactional
    @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount + 1, r.isHelpful = true " +
           "WHERE r.id = :reviewId")
    void incrementHelpfulCount(@Param("reviewId") UUID reviewId);
    
    /**
     * Get monthly review trend for a provider
     */
    @Query(value = "SELECT DATE_TRUNC('month', created_at) as month, " +
           "COUNT(*) as review_count, " +
           "AVG((rating_quality + rating_reliability + rating_communication) / 3.0) as avg_rating " +
           "FROM reviews " +
           "WHERE provider_id = :providerId " +
           "AND created_at >= :startDate " +
           "GROUP BY DATE_TRUNC('month', created_at) " +
           "ORDER BY month DESC", 
           nativeQuery = true)
    List<Object[]> getMonthlyReviewTrend(@Param("providerId") UUID providerId, 
                                         @Param("startDate") java.time.LocalDateTime startDate);
    
    /**
     * Get reviews by rating range
     */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId " +
           "AND ((r.ratingQuality + r.ratingReliability + r.ratingCommunication) / 3) BETWEEN :minRating AND :maxRating")
    List<Review> findByRatingRange(@Param("providerId") UUID providerId,
                                    @Param("minRating") double minRating,
                                    @Param("maxRating") double maxRating);
    
    /**
     * Count reviews with comments (text reviews)
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.providerId = :providerId AND LENGTH(r.comment) > 10")
    Long countReviewsWithComments(@Param("providerId") UUID providerId);
    
    /**
     * Get average response time to reviews (when provider responds to customer review)
     * Note: This assumes you add a provider_response field to Review entity
     */
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (r.providerRespondedAt - r.createdAt))) " +
           "FROM Review r WHERE r.providerId = :providerId AND r.providerRespondedAt IS NOT NULL")
    Double getAverageResponseTimeToReviews(@Param("providerId") UUID providerId);
}