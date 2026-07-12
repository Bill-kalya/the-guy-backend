package com.theguy.app.service;

import com.theguy.app.entity.RiskScore;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.entity.Review;
import com.theguy.app.entity.Dispute;
import com.theguy.app.entity.Job;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final RiskScoreRepository riskScoreRepository;
    private final ProviderRepository providerRepository;
    private final JobRepository jobRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    private final DisputeRepository disputeRepository;

    @Transactional
    public RiskScore calculateRiskScore(UUID userId, String userType) {
        Map<String, Object> factors = new HashMap<>();
        int score = 0;

        if ("PROVIDER".equalsIgnoreCase(userType)) {
            score = calculateProviderRisk(userId, factors);
        } else {
            score = calculateCustomerRisk(userId, factors);
        }

        String riskLevel = getRiskLevel(score);
        String recommendations = generateRecommendations(score, factors);

        // ensure JSONB string
        String factorsJson;
        try {
            factorsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(factors);
        } catch (Exception e) {
            factorsJson = "{}";
        }

        RiskScore risk = new RiskScore();
        risk.setUserId(userId);
        risk.setUserType(userType.toUpperCase(Locale.ROOT));
        risk.setScore(score);
        risk.setRiskLevel(riskLevel);
        risk.setFactors(factorsJson);
        risk.setRecommendations(recommendations);
        risk.setCalculatedAt(LocalDateTime.now());

        return riskScoreRepository.save(risk);
    }

    public Map<String, Object> getActiveFraudAlerts() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("activeRiskScores", riskScoreRepository.countActiveRiskScores());
        dashboard.put("criticalRiskUsers", riskScoreRepository.countByRiskLevel("CRITICAL"));
        dashboard.put("highRiskUsers", riskScoreRepository.countByRiskLevel("HIGH"));
        return dashboard;
    }

    public RiskScore getCurrentRiskScore(UUID userId) {
        return riskScoreRepository.findTopByUserIdOrderByCalculatedAtDesc(userId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RiskScore> getRiskScores(String riskLevel, org.springframework.data.domain.Pageable pageable) {
        if (riskLevel == null || riskLevel.isBlank() || "ALL".equalsIgnoreCase(riskLevel)) {
            return riskScoreRepository.findAll(pageable);
        }
        return riskScoreRepository.findByRiskLevel(riskLevel.toUpperCase(Locale.ROOT), pageable);
    }

    private int calculateProviderRisk(UUID providerId, Map<String, Object> factors) {
        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null) return 100;

        double cancellationRate = provider.getJobsCancelled() /
                (double) Math.max(1, provider.getJobsCompleted() + provider.getJobsCancelled());
        factors.put("cancellationRate", cancellationRate);

        int score = 0;
        if (cancellationRate > 0.5) score += 30;
        else if (cancellationRate > 0.3) score += 20;
        else if (cancellationRate > 0.1) score += 10;

        double rating = provider.getRatingAvg();
        factors.put("rating", rating);
        if (rating < 2.0) score += 20;
        else if (rating < 3.0) score += 10;
        else if (rating < 4.0) score += 5;

        long disputeCount = disputeRepository.countByStatus(Dispute.DisputeStatus.OPEN) ;
        // note: simplified; without providerId-specific dispute query in repo
        factors.put("disputeCount", disputeCount);
        if (disputeCount > 10) score += 20;
        else if (disputeCount > 5) score += 10;
        else if (disputeCount > 2) score += 5;

        VerificationLevel level = provider.getVerificationLevel();
        factors.put("verificationLevel", level != null ? level.name() : null);
        if (level == VerificationLevel.NONE) score += 15;
        else if (level == VerificationLevel.BASIC) score += 5;

        long ageDays = Duration.between(provider.getCreatedAt(), LocalDateTime.now()).toDays();
        factors.put("accountAgeDays", ageDays);
        if (ageDays < 30) score += 15;
        else if (ageDays < 90) score += 8;

        return Math.min(100, score);
    }

    private int calculateCustomerRisk(UUID customerId, Map<String, Object> factors) {
        User user = userRepository.findById(customerId).orElse(null);
        if (user == null) return 100;

        long openDisputes = disputeRepository.countByOpenStatus();
        factors.put("openDisputes", openDisputes);
        int score = 0;
        if (openDisputes > 5) score += 25;
        else if (openDisputes > 2) score += 15;
        else if (openDisputes > 0) score += 5;

        long totalJobs = jobRepository.findByCustomerId(customerId).size();
        long cancelled = 0; // TODO: wire cancellation query from JobRepository statuses

        double cancellationRate = totalJobs > 0 ? cancelled / (double) totalJobs : 0;
        factors.put("cancellationRate", cancellationRate);
        if (cancellationRate > 0.5) score += 25;
        else if (cancellationRate > 0.3) score += 15;
        else if (cancellationRate > 0.1) score += 8;

        factors.put("verified", user.isVerified());
        if (!user.isVerified()) score += 15;

        long ageDays = Duration.between(user.getCreatedAt(), LocalDateTime.now()).toDays();
        factors.put("accountAgeDays", ageDays);
        if (ageDays < 30) score += 15;
        else if (ageDays < 90) score += 8;

        long reviews = reviewRepository.findByCustomerId(customerId, org.springframework.data.domain.PageRequest.of(0, 1)).size();
        factors.put("reviewCount", reviews);
        if (reviews > 50) score += 20;

        return Math.min(100, score);
    }

    private String getRiskLevel(int score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 50) return "HIGH";
        if (score >= 20) return "MEDIUM";
        return "LOW";
    }

    private String generateRecommendations(int score, Map<String, Object> factors) {
        List<String> recs = new ArrayList<>();
        if (score >= 80) {
            recs.add("⚠️ IMMEDIATE ACTION REQUIRED: Suspend user account");
            recs.add("Review all recent transactions");
            recs.add("Flag for manual review by senior admin");
        } else if (score >= 50) {
            recs.add("📋 Require re-verification");
            recs.add("Restrict high-value transactions");
            recs.add("Monitor activity for 30 days");
        } else if (score >= 20) {
            recs.add("🔍 Monitor activity");
            recs.add("Review if suspicious patterns continue");
        }

        Object cancellationRate = factors.get("cancellationRate");
        if (cancellationRate instanceof Number && ((Number) cancellationRate).doubleValue() > 0.3) {
            recs.add("📊 High cancellation rate detected - consider warning");
        }

        return String.join("\n", recs);
    }
}

