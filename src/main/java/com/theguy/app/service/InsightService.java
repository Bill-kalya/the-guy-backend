package com.theguy.app.service;

import com.theguy.app.dto.InsightDTO;
import com.theguy.app.entity.ProviderInsight;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.repository.ProviderInsightRepository;
import com.theguy.app.repository.ProviderStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final ProviderInsightRepository insightRepository;
    private final ProviderStatisticsRepository providerStatisticsRepository;

    @Transactional
    public void generateInsights(UUID providerId) {
        Optional<ProviderStatistics> statsOpt = providerStatisticsRepository.findById(providerId);
        if (statsOpt.isEmpty()) return;

        ProviderStatistics stats = statsOpt.get();
        insightRepository.deleteByProviderId(providerId);

        Map<String, Double> categoryScores = new LinkedHashMap<>();
        categoryScores.put("Professionalism", stats.getProfessionalismScore());
        categoryScores.put("Communication", stats.getCommunicationScore());
        categoryScores.put("Timeliness", stats.getTimelinessScore());
        categoryScores.put("Work Quality", stats.getWorkQualityScore());
        categoryScores.put("Reliability", stats.getReliabilityScore());
        categoryScores.put("Courtesy", stats.getCourtesyScore());
        categoryScores.put("Value for Money", stats.getValueScore());

        List<Map.Entry<String, Double>> sorted = categoryScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        List<ProviderInsight> insights = new ArrayList<>();

        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            if (entry.getValue() >= 80) {
                ProviderInsight insight = new ProviderInsight();
                insight.setProviderId(providerId);
                insight.setInsightType("STRENGTH");
                insight.setCategory(entry.getKey());
                insight.setMessage(generateStrengthMessage(entry.getKey(), entry.getValue()));
                insight.setFrequency((int) Math.round(entry.getValue()));
                insight.setGeneratedAt(LocalDateTime.now());
                insights.add(insight);
            }
        }

        for (int i = sorted.size() - 1; i >= Math.max(0, sorted.size() - 3); i--) {
            Map.Entry<String, Double> entry = sorted.get(i);
            if (entry.getValue() < 85) {
                ProviderInsight insight = new ProviderInsight();
                insight.setProviderId(providerId);
                insight.setInsightType("IMPROVEMENT");
                insight.setCategory(entry.getKey());
                insight.setMessage(generateImprovementMessage(entry.getKey(), entry.getValue()));
                insight.setFrequency((int) Math.round(entry.getValue()));
                insight.setGeneratedAt(LocalDateTime.now());
                insights.add(insight);
            }
        }

        insightRepository.saveAll(insights);
        log.info("Generated {} insights for provider {}", insights.size(), providerId);
    }

    @Transactional(readOnly = true)
    public InsightDTO getInsights(UUID providerId) {
        List<ProviderInsight> insights = insightRepository.findByProviderId(providerId);

        List<InsightDTO.InsightItem> strengths = insights.stream()
                .filter(i -> "STRENGTH".equals(i.getInsightType()))
                .map(i -> InsightDTO.InsightItem.builder()
                        .category(i.getCategory())
                        .message(i.getMessage())
                        .score((double) i.getFrequency())
                        .build())
                .collect(Collectors.toList());

        List<InsightDTO.InsightItem> improvements = insights.stream()
                .filter(i -> "IMPROVEMENT".equals(i.getInsightType()))
                .map(i -> InsightDTO.InsightItem.builder()
                        .category(i.getCategory())
                        .message(i.getMessage())
                        .score((double) i.getFrequency())
                        .build())
                .collect(Collectors.toList());

        String trend = computeTrend(providerId);

        return InsightDTO.builder()
                .strengths(strengths)
                .improvements(improvements)
                .trend(trend)
                .build();
    }

    private String computeTrend(UUID providerId) {
        Optional<ProviderStatistics> statsOpt = providerStatisticsRepository.findById(providerId);
        if (statsOpt.isEmpty()) return "No review data available";

        ProviderStatistics stats = statsOpt.get();
        if (stats.getReviewCount() == 0) return "No review data available";

        double sqs = stats.getSqs();
        if (sqs >= 90) return "Your SQS is excellent and among the top providers";
        if (sqs >= 80) return "Your SQS is strong with room for minor improvements";
        if (sqs >= 70) return "Your SQS is decent — focus on weak categories to improve";
        return "Your SQS needs attention — check improvement suggestions below";
    }

    private String generateStrengthMessage(String category, double score) {
        return switch (category) {
            case "Professionalism" -> "Customers frequently praise your professionalism";
            case "Communication" -> "Your clear communication keeps customers informed";
            case "Timeliness" -> "Consistently arrive on time, customers love this";
            case "Work Quality" -> "Consistently deliver clean, high-quality work";
            case "Reliability" -> "Customers trust you to complete jobs reliably";
            case "Courtesy" -> "Your friendly attitude makes customers comfortable";
            case "Value for Money" -> "Customers feel they get great value for the price";
            default -> "Strong performance in " + category;
        };
    }

    private String generateImprovementMessage(String category, double score) {
        return switch (category) {
            case "Professionalism" -> "Consider more formal presentation and communication";
            case "Communication" -> "Try updating customers before arrival";
            case "Timeliness" -> "Focus on punctuality for better ratings";
            case "Work Quality" -> "Pay extra attention to finishing details";
            case "Reliability" -> "Ensure consistent follow-through on commitments";
            case "Courtesy" -> "Small gestures of courtesy improve customer experience";
            case "Value for Money" -> "Communicate value clearly before starting work";
            default -> "Room to improve in " + category;
        };
    }
}
