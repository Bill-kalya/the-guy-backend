package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class PerformanceDTO {
    private Double acceptanceRate;
    private Double completionRate;
    private Double avgResponseTime;
    private Integer repeatCustomerCount;
    private Double cancellationRate;
    private String ranking;
    private Map<String, String> trend;
}
