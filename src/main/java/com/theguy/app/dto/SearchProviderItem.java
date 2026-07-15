package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SearchProviderItem {
    private UUID id;
    private String businessName;
    private Double distance;
    private Integer etaMinutes;
    private Double serviceQualityScore;
    private Boolean verified;
    private Double rating;
    private Integer completedJobs;
}
