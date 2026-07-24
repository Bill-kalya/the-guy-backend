package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ReviewSummaryDTO {
    private Double overallSqs;
    private Long totalReviews;
    private Map<String, Double> categories;
    private String recentTrend;
}
