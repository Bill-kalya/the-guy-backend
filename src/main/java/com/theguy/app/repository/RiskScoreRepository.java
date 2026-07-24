package com.theguy.app.repository;

import com.theguy.app.entity.RiskScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findTopByUserIdOrderByCalculatedAtDesc(UUID userId);

    Page<RiskScore> findByRiskLevel(String riskLevel, Pageable pageable);

    long countByScoreGreaterThan(int score);

    long countByRiskLevel(String riskLevel);

    @Query("SELECT COALESCE(COUNT(r),0) FROM RiskScore r WHERE r.expiresAt > CURRENT_TIMESTAMP")
    long countActiveRiskScores();

    @Query(value = """
        SELECT DISTINCT ON (r.user_id) r.*
        FROM risk_scores r
        WHERE r.user_id IN :userIds
        ORDER BY r.user_id, r.calculated_at DESC
        """, nativeQuery = true)
    List<RiskScore> findLatestForUserIds(@Param("userIds") List<UUID> userIds);
}

