package com.theguy.app.dto;

import com.theguy.app.enums.ProviderBadge;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class SearchProviderItem {
    private UUID id;
    private String businessName;
    private Double distance;
    private Integer etaMinutes;
    private Double serviceQualityScore;
    private ProviderBadge badge;
    private Boolean verified;
    private Double rating;
    private Integer completedJobs;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double searchScore;
}
