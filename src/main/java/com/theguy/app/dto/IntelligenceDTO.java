package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class IntelligenceDTO {
    private String topEarningCategory;
    private String topEarningDay;
    private String topEarningHour;
    private String topEarningArea;
    private String avgJobDuration;
    private Double conversionRate;
    private Double returnCustomerRate;
    private String monthlyTrend;
    private List<String> bestPerformingDays;
    private List<CategoryBreakdown> categoryBreakdown;

    @Data
    @Builder
    public static class CategoryBreakdown {
        private String category;
        private Integer jobs;
        private Double earnings;
        private Double percentage;
    }
}
