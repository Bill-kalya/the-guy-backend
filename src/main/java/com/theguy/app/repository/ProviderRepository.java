package com.theguy.app.repository;

import com.theguy.app.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByUserId(UUID userId);

    Optional<Provider> findByClaimCode(String claimCode);

    List<Provider> findByIsOnlineTrue();

    List<Provider> findByCategoryId(String categoryId);

    List<Provider> findByIdInAndCategoryId(List<UUID> providerIds, String categoryId);

    List<Provider> findByIdInAndCategoryIdIgnoreCase(List<UUID> providerIds, String categoryId);

    @Query("SELECT DISTINCT p FROM Provider p LEFT JOIN FETCH p.portfolioImages pi WHERE p.id IN :providerIds")
    List<Provider> findAllByIdWithPortfolio(@Param("providerIds") List<UUID> providerIds);

    long countByProviderStatus(String providerStatus);

    long countByIsOnlineTrue();

    /**
     * Providers currently flagged online but whose last heartbeat (location
     * update or availability toggle) is older than the grace window. These are
     * "ghost" providers and are taken offline by the scheduled sweep.
     */
    @Query("SELECT p.id FROM Provider p WHERE p.isOnline = true AND p.lastActiveAt < :threshold")
    List<UUID> findOnlineSinceBefore(@Param("threshold") LocalDateTime threshold);

    /** Same as {@link #countByIsOnlineTrue()} but requires a fresh heartbeat. */
    long countByIsOnlineTrueAndLastActiveAtAfter(LocalDateTime threshold);

    /** Heartbeat: refresh last-seen without touching any other column. */
    @Modifying
    @Transactional
    @Query("UPDATE Provider p SET p.lastActiveAt = :timestamp WHERE p.id = :providerId")
    void updateLastActiveAt(@Param("providerId") UUID providerId, @Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT COALESCE(AVG(p.ratingAvg), 0.0) FROM Provider p")
    Double findAverageRating();

    long countByVerificationLevelIn(java.util.Collection<com.theguy.app.enums.VerificationLevel> levels);

    @Query(value = """
        SELECT p.*, COALESCE(rs.score, 0) AS risk_score_val, rs.risk_level AS risk_level_val
        FROM providers p
        LEFT JOIN LATERAL (
            SELECT score, risk_level
            FROM risk_scores r
            WHERE r.user_id = (SELECT u.id FROM users u WHERE u.id = p.user_id)
            ORDER BY r.calculated_at DESC
            LIMIT 1
        ) rs ON true
        WHERE (:status IS NULL OR :status = '' OR :status = 'ALL' OR p.provider_status = :status)
        ORDER BY risk_score_val DESC, p.created_at DESC
        """, countQuery = """
        SELECT COUNT(*)
        FROM providers p
        WHERE (:status IS NULL OR :status = '' OR :status = 'ALL' OR p.provider_status = :status)
        """, nativeQuery = true)
    Page<Object[]> findModerationQueue(@Param("status") String status, Pageable pageable);
}
