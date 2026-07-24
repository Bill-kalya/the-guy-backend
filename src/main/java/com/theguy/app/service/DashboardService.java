package com.theguy.app.service;

import com.theguy.app.dto.DashboardSummaryDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ProviderStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProviderRepository providerRepository;
    private final JobRepository jobRepository;
    private final ProviderStatisticsRepository providerStatisticsRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary(UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        List<com.theguy.app.entity.Job> allJobs = jobRepository.findByProviderId(providerId);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();

        long todayJobsCount = allJobs.stream()
                .filter(j -> j.getCreatedAt() != null && j.getCreatedAt().isAfter(startOfDay))
                .count();
        long weekJobsCount = allJobs.stream()
                .filter(j -> j.getCreatedAt() != null && j.getCreatedAt().isAfter(startOfWeek))
                .count();
        long totalCompleted = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .count();
        long activeCount = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.IN_PROGRESS)
                .count();

        double todayEarnings = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED && j.getCreatedAt() != null && j.getCreatedAt().isAfter(startOfDay))
                .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                .sum();
        double weekEarnings = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED && j.getCreatedAt() != null && j.getCreatedAt().isAfter(startOfWeek))
                .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                .sum();
        double monthEarnings = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED && j.getCreatedAt() != null && j.getCreatedAt().isAfter(startOfMonth))
                .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                .sum();
        double totalEarnings = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                .sum();

        double availableBalance = 0;
        double pendingBalance = 0;
        String currency = "KES";
        try {
            var wallet = walletService.getWallet(providerId);
            availableBalance = wallet.getAvailableBalance();
            pendingBalance = wallet.getPendingBalance();
            currency = wallet.getCurrency();
        } catch (Exception e) {
            log.warn("Could not load wallet for provider {}: {}", providerId, e.getMessage());
        }

        double avgRating = 0;
        long totalReviews = 0;
        Optional<ProviderStatistics> statsOpt = providerStatisticsRepository.findById(providerId);
        if (statsOpt.isPresent()) {
            ProviderStatistics stats = statsOpt.get();
            avgRating = stats.getSqs();
            totalReviews = stats.getReviewCount();
        }

        double completionRate = (provider.getJobsCompleted() + provider.getJobsCancelled()) > 0
                ? (double) provider.getJobsCompleted() / (provider.getJobsCompleted() + provider.getJobsCancelled()) * 100
                : 0.0;
        double cancellationRate = (provider.getJobsCompleted() + provider.getJobsCancelled()) > 0
                ? (double) provider.getJobsCancelled() / (provider.getJobsCompleted() + provider.getJobsCancelled()) * 100
                : 0.0;

        String ranking = calculateRanking(completionRate, statsOpt);

        List<Map<String, Object>> weeklyChart = buildWeeklyChart(allJobs);

        return DashboardSummaryDTO.builder()
                .todayEarnings(todayEarnings)
                .weekEarnings(weekEarnings)
                .monthEarnings(monthEarnings)
                .totalEarnings(totalEarnings)
                .currency(currency)
                .todayJobs((int) todayJobsCount)
                .weekJobs((int) weekJobsCount)
                .totalJobsCompleted((int) totalCompleted)
                .activeJobs((int) activeCount)
                .averageRating(avgRating)
                .totalReviews(totalReviews)
                .responseRate(provider.getResponseRate() * 100)
                .completionRate(completionRate)
                .cancellationRate(cancellationRate)
                .ranking(ranking)
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .weeklyChart(weeklyChart)
                .build();
    }

    private List<Map<String, Object>> buildWeeklyChart(List<com.theguy.app.entity.Job> allJobs) {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);

        Map<String, Double> dailyTotals = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            String label = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            dailyTotals.put(label, 0.0);
        }

        for (com.theguy.app.entity.Job job : allJobs) {
            if (job.getStatus() == JobStatus.COMPLETED && job.getCreatedAt() != null) {
                LocalDate jobDate = job.getCreatedAt().toLocalDate();
                if (!jobDate.isBefore(startOfWeek) && !jobDate.isAfter(today)) {
                    String dayLabel = jobDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    dailyTotals.merge(dayLabel, job.getFinalPrice() != null ? job.getFinalPrice() : 0.0, Double::sum);
                }
            }
        }

        return dailyTotals.entrySet().stream()
                .map(e -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("day", e.getKey());
                    point.put("amount", e.getValue());
                    return point;
                })
                .collect(Collectors.toList());
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
