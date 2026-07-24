package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSummaryDTO {
    private Double todayEarnings;
    private Double weekEarnings;
    private Double monthEarnings;
    private Double totalEarnings;
    private String currency;
    private Integer todayJobs;
    private Integer weekJobs;
    private Integer totalJobsCompleted;
    private Integer activeJobs;
    private Double averageRating;
    private Long totalReviews;
    private Double responseRate;
    private Double completionRate;
    private Double cancellationRate;
    private String ranking;
    private Double availableBalance;
    private Double pendingBalance;
    private List<Map<String, Object>> weeklyChart;
}
