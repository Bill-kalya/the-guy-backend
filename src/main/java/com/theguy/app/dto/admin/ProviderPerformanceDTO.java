package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderPerformanceDTO {
    private Double ratingAvg;
    private Integer totalReviews;
    private Long completedJobs;
    private Long cancelledJobs;
    private Double cancellationRate;
    private Double responseRate;
    private Double repeatClientsPercentage;
}
