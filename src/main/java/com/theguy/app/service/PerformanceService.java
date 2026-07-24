package com.theguy.app.service;

import com.theguy.app.dto.PerformanceDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderPerformance;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.repository.ProviderPerformanceRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ProviderStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final ProviderPerformanceRepository performanceRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStatisticsRepository providerStatisticsRepository;

    @Transactional
    public ProviderPerformance recalculate(UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        Optional<ProviderStatistics> statsOpt = providerStatisticsRepository.findById(providerId);

        double completionRate = provider.getJobsCompleted() > 0
                ? (double) provider.getJobsCompleted() / (provider.getJobsCompleted() + provider.getJobsCancelled()) * 100
                : 0.0;
        double cancellationRate = (provider.getJobsCompleted() + provider.getJobsCancelled()) > 0
                ? (double) provider.getJobsCancelled() / (provider.getJobsCompleted() + provider.getJobsCancelled()) * 100
                : 0.0;

        String ranking = calculateRanking(completionRate, statsOpt);

        ProviderPerformance perf = performanceRepository.findById(providerId)
                .orElse(new ProviderPerformance());
        perf.setProviderId(providerId);
        perf.setAcceptanceRate(provider.getResponseRate() * 100);
        perf.setCompletionRate(completionRate);
        perf.setCancellationRate(cancellationRate);
        perf.setResponseRate(provider.getResponseRate() * 100);
        perf.setAvgResponseTime("1m 42s");
        perf.setRepeatCustomerCount((int) provider.getRepeatClientsPercentage());
        perf.setRanking(ranking);
        perf.setCalculatedAt(LocalDateTime.now());

        return performanceRepository.save(perf);
    }

    @Transactional(readOnly = true)
    public Optional<ProviderPerformance> getPerformance(UUID providerId) {
        return performanceRepository.findById(providerId);
    }

    @Transactional(readOnly = true)
    public PerformanceDTO getPerformanceDTO(UUID providerId) {
        ProviderPerformance perf = performanceRepository.findById(providerId)
                .orElseGet(() -> recalculate(providerId));

        Optional<ProviderPerformance> previousOpt = getPreviousPerformance(providerId);

        Map<String, String> trend = new HashMap<>();
        if (previousOpt.isPresent()) {
            ProviderPerformance prev = previousOpt.get();
            trend.put("acceptanceRate", formatTrend(perf.getAcceptanceRate(), prev.getAcceptanceRate(), "%"));
            trend.put("completionRate", formatTrend(perf.getCompletionRate(), prev.getCompletionRate(), "%"));
            trend.put("avgResponseTime", prev.getAvgResponseTime() != null ? perf.getAvgResponseTime() : "No prior data");
        } else {
            trend.put("acceptanceRate", "No prior data");
            trend.put("completionRate", "No prior data");
            trend.put("avgResponseTime", "No prior data");
        }

        return PerformanceDTO.builder()
                .acceptanceRate(perf.getAcceptanceRate())
                .completionRate(perf.getCompletionRate())
                .avgResponseTime(0.0)
                .repeatCustomerCount(perf.getRepeatCustomerCount())
                .cancellationRate(perf.getCancellationRate())
                .ranking(perf.getRanking())
                .trend(trend)
                .build();
    }

    private Optional<ProviderPerformance> getPreviousPerformance(UUID providerId) {
        return performanceRepository.findById(providerId)
                .filter(p -> p.getCalculatedAt() != null && p.getCalculatedAt().isBefore(LocalDateTime.now().minusHours(1)));
    }

    private String formatTrend(Double current, Double previous, String unit) {
        if (current == null || previous == null || previous == 0) return "No prior data";
        double diff = current - previous;
        String sign = diff >= 0 ? "+" : "";
        return sign + String.format("%.1f%s", diff, unit);
    }

    private String calculateRanking(double completionRate, Optional<ProviderStatistics> statsOpt) {
        if (completionRate >= 98 && statsOpt.isPresent() && statsOpt.get().getSqs() >= 90) {
            return "Top 5% in your area";
        } else if (completionRate >= 95 && statsOpt.isPresent() && statsOpt.get().getSqs() >= 85) {
            return "Top 10% in your area";
        } else if (completionRate >= 90) {
            return "Top 25% in your area";
        }
        return "Building reputation";
    }
}
