package com.theguy.app.service;

import com.theguy.app.entity.*;
import com.theguy.app.enums.DisputeStatus;
import com.theguy.app.enums.Role;
import com.theguy.app.exception.ProviderNotFoundException;
import com.theguy.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustSafetyService {

    private final RiskScoreRepository riskScoreRepository;
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final DisputeRepository disputeRepository;
    private final AdminActionRepository adminActionRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getSummary() {
        long totalProviders = providerRepository.count();
        long activeRiskScores = riskScoreRepository.countActiveRiskScores();
        long criticalCount = riskScoreRepository.countByRiskLevel("CRITICAL");
        long highCount = riskScoreRepository.countByRiskLevel("HIGH");
        long mediumCount = riskScoreRepository.countByRiskLevel("MEDIUM");
        long lowCount = riskScoreRepository.countByRiskLevel("LOW");

        long openDisputes = disputeRepository.countByStatus(DisputeStatus.OPEN);
        long investigatingDisputes = disputeRepository.countByStatus(DisputeStatus.INVESTIGATING);

        long suspendedProviders = providerRepository.countByProviderStatus("SUSPENDED");
        long bannedProviders = providerRepository.countByProviderStatus("BANNED");

        double fraudRiskPct = totalProviders > 0
                ? BigDecimal.valueOf(criticalCount + highCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalProviders), 1, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProviders", totalProviders);
        summary.put("fraudRiskPercent", fraudRiskPct);
        summary.put("suspiciousAccounts", criticalCount + highCount);
        summary.put("openDisputes", openDisputes);
        summary.put("investigatingDisputes", investigatingDisputes);
        summary.put("suspendedProviders", suspendedProviders);
        summary.put("bannedProviders", bannedProviders);
        summary.put("riskDistribution", Map.of(
                "critical", criticalCount,
                "high", highCount,
                "medium", mediumCount,
                "low", lowCount
        ));
        summary.put("activeRiskScores", activeRiskScores);

        return summary;
    }

    public List<Map<String, Object>> getCriticalAlerts(int limit) {
        if (limit <= 0) limit = 10;

        List<RiskScore> critical = riskScoreRepository
                .findByRiskLevel("CRITICAL", PageRequest.of(0, limit)).getContent();
        List<RiskScore> high = riskScoreRepository
                .findByRiskLevel("HIGH", PageRequest.of(0, limit)).getContent();

        List<RiskScore> merged = new ArrayList<>(critical);
        merged.addAll(high);

        Set<UUID> userIds = merged.stream().map(RiskScore::getUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return merged.stream()
                .sorted(Comparator.comparing(RiskScore::getCalculatedAt).reversed())
                .limit(limit)
                .map(rs -> {
                    User user = userMap.get(rs.getUserId());
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("id", rs.getId());
                    alert.put("userId", rs.getUserId());
                    alert.put("userName", user != null ? user.getFullName() : "Unknown");
                    alert.put("email", user != null ? user.getEmail() : "");
                    alert.put("userType", rs.getUserType());
                    alert.put("score", rs.getScore());
                    alert.put("riskLevel", rs.getRiskLevel());
                    alert.put("factors", rs.getFactors());
                    alert.put("recommendations", rs.getRecommendations());
                    alert.put("calculatedAt", rs.getCalculatedAt());
                    return alert;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHeatmap() {
        List<ProviderLocation> locations = providerLocationRepository.findAll();
        if (locations.isEmpty()) return List.of();

        List<UUID> allProviderIds = locations.stream()
                .map(ProviderLocation::getProviderId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, RiskScore> latestScores = riskScoreRepository.findLatestForUserIds(allProviderIds)
                .stream()
                .collect(Collectors.toMap(RiskScore::getUserId, rs -> rs, (a, b) -> a));

        Map<String, List<UUID>> cityProviders = new LinkedHashMap<>();
        for (ProviderLocation loc : locations) {
            String city = loc.getCity();
            if (city == null || city.isBlank()) {
                city = resolveCityFromCoords(loc.getLatitude(), loc.getLongitude());
            }
            cityProviders.computeIfAbsent(city, k -> new ArrayList<>()).add(loc.getProviderId());
        }

        List<Map<String, Object>> heatmap = new ArrayList<>();
        for (Map.Entry<String, List<UUID>> entry : cityProviders.entrySet()) {
            String city = entry.getKey();
            List<UUID> providerIds = entry.getValue();

            double avgRisk = providerIds.stream()
                    .mapToLong(id -> {
                        RiskScore rs = latestScores.get(id);
                        return rs != null ? rs.getScore() : 0;
                    })
                    .average()
                    .orElse(0.0);

            long highRiskCount = providerIds.stream()
                    .filter(id -> {
                        RiskScore rs = latestScores.get(id);
                        return rs != null && rs.getScore() >= 50;
                    })
                    .count();

            Map<String, Object> cityData = new LinkedHashMap<>();
            cityData.put("city", city);
            cityData.put("providerCount", providerIds.size());
            cityData.put("avgRiskScore", Math.round(avgRisk * 10.0) / 10.0);
            cityData.put("highRiskCount", highRiskCount);
            cityData.put("riskLevel", avgRisk >= 80 ? "CRITICAL" : avgRisk >= 50 ? "HIGH" : avgRisk >= 20 ? "MEDIUM" : "LOW");
            heatmap.add(cityData);
        }

        heatmap.sort((a, b) -> Double.compare((double) b.get("avgRiskScore"), (double) a.get("avgRiskScore")));
        return heatmap;
    }

    public Page<Map<String, Object>> getModerationQueue(String status, int page, int size) {
        String normalizedStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null : status.toUpperCase();

        Page<Object[]> dbPage = providerRepository.findModerationQueue(normalizedStatus, PageRequest.of(page, size));

        // Native query returns p.* as raw columns + risk_score_val + risk_level_val.
        // Extract provider IDs (column 0 of each row) and fetch entities in bulk.
        List<UUID> providerIds = dbPage.getContent().stream()
                .map(row -> (UUID) row[0])
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, Provider> providerMap = providerIds.isEmpty()
                ? Map.of()
                : providerRepository.findAllById(providerIds).stream()
                    .collect(Collectors.toMap(Provider::getId, p -> p));

        List<Map<String, Object>> items = dbPage.getContent().stream().map(row -> {
            UUID providerId = (UUID) row[0];
            Provider p = providerMap.get(providerId);

            Object riskScoreRaw = row[row.length - 2];
            Object riskLevelRaw = row[row.length - 1];

            Integer riskScore = riskScoreRaw instanceof Number ? ((Number) riskScoreRaw).intValue() : null;
            String riskLevel = riskLevelRaw != null ? riskLevelRaw.toString() : null;

            Map<String, Object> item = new LinkedHashMap<>();
            if (p != null) {
                User user = p.getUser();
                item.put("providerId", p.getId());
                item.put("userId", user != null ? user.getId() : null);
                item.put("fullName", user != null ? user.getFullName() : "Unknown");
                item.put("email", user != null ? user.getEmail() : "");
                item.put("category", p.getCategoryId());
                item.put("status", p.getProviderStatus() != null ? p.getProviderStatus() : "ACTIVE");
                item.put("isOnline", p.isOnline());
                item.put("ratingAvg", p.getRatingAvg());
                item.put("jobsCompleted", p.getJobsCompleted());
                item.put("jobsCancelled", p.getJobsCancelled());
                item.put("verificationLevel", p.getVerificationLevel() != null ? p.getVerificationLevel().name() : "NONE");
                item.put("createdAt", p.getCreatedAt());
            } else {
                item.put("providerId", providerId);
                item.put("fullName", "Unknown");
            }
            item.put("riskScore", riskScore);
            item.put("riskLevel", riskLevel);
            return item;
        }).collect(Collectors.toList());

        return new PageImpl<>(items, dbPage.getPageable(), dbPage.getTotalElements());
    }

    @Transactional
    public void suspendProvider(UUID providerId, String reason, UUID adminId, String ip, String userAgent) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        provider.setProviderStatus("SUSPENDED");
        providerRepository.save(provider);

        logAdminAction(adminId, AdminAction.ActionType.PROVIDER_SUSPEND, providerId, reason, ip, userAgent);
        log.info("Provider {} suspended by admin {}: {}", providerId, adminId, reason);
    }

    @Transactional
    public void banProvider(UUID providerId, String reason, UUID adminId, String ip, String userAgent) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        provider.setProviderStatus("BANNED");
        provider.setOnline(false);
        providerRepository.save(provider);

        logAdminAction(adminId, AdminAction.ActionType.PROVIDER_BAN, providerId, reason, ip, userAgent);
        log.info("Provider {} banned by admin {}: {}", providerId, adminId, reason);
    }

    @Transactional
    public void reinstateProvider(UUID providerId, String reason, UUID adminId, String ip, String userAgent) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        provider.setProviderStatus("ACTIVE");
        providerRepository.save(provider);

        logAdminAction(adminId, AdminAction.ActionType.PROVIDER_UNSUSPEND, providerId, reason, ip, userAgent);
        log.info("Provider {} reinstated by admin {}: {}", providerId, adminId, reason);
    }

    @Transactional
    public void demoteProvider(UUID providerId, String reason, UUID adminId, String ip, String userAgent) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));

        provider.setProviderStatus("INACTIVE");
        provider.setOnline(false);
        providerRepository.save(provider);

        User user = provider.getUser();
        if (user != null && user.getRole() == Role.PROVIDER) {
            user.setRole(Role.CUSTOMER);
            userRepository.save(user);
        }

        logAdminAction(adminId, AdminAction.ActionType.PROVIDER_DEMOTE, providerId, reason, ip, userAgent);
        log.info("Provider {} demoted to customer by admin {}: {}", providerId, adminId, reason);
    }

    private void logAdminAction(UUID adminId, AdminAction.ActionType actionType,
                                 UUID targetId, String reason, String ip, String userAgent) {
        AdminAction action = new AdminAction();
        action.setAdminId(adminId);
        action.setActionType(actionType);
        action.setTargetId(targetId.toString());
        action.setTargetType("PROVIDER");
        action.setDetails(reason != null ? reason : actionType.name());
        action.setIpAddress(ip);
        action.setUserAgent(userAgent);
        adminActionRepository.save(action);
    }

    private String resolveCityFromCoords(double lat, double lng) {
        if (lat >= -1.4 && lat <= -1.1 && lng >= 36.6 && lng <= 37.0) return "Nairobi";
        if (lat >= -4.2 && lat <= -3.8 && lng >= 39.5 && lng <= 40.0) return "Mombasa";
        if (lat >= -0.2 && lat <= 0.1 && lng >= 34.6 && lng <= 35.0) return "Kisumu";
        if (lat >= 0.4 && lat <= 0.7 && lng >= 35.1 && lng <= 35.4) return "Eldoret";
        if (lat >= -0.5 && lat <= -0.1 && lng >= 36.0 && lng <= 36.4) return "Nakuru";
        if (lat >= -1.0 && lat <= -0.6 && lng >= 37.0 && lng <= 37.4) return "Thika";
        if (lat >= -0.4 && lat <= -0.1 && lng >= 36.8 && lng <= 37.1) return "Kiambu";
        if (lat >= -0.7 && lat <= -0.4 && lng >= 37.2 && lng <= 37.6) return "Machakos";
        return "Other";
    }
}
