package com.theguy.app.dto;

import com.theguy.app.enums.JobStatus;
import com.theguy.app.enums.Urgency;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class JobResponseDTO {
    private UUID id;
    private String serviceCategory;
    private String description;
    private JobStatus status;
    private Urgency urgency;
    private Double priceEstimateMin;
    private Double priceEstimateMax;
    private Double finalPrice;
    private LocationDTO location;
    private ProviderSummaryDTO provider;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    
    @Data
    @Builder
    public static class ProviderSummaryDTO {
        private UUID id;
        private String fullName;
        private Double rating;
        private Integer jobsCompleted;
        private String verificationLevel;
    }
    
    @Data
    @Builder
    public static class LocationDTO {
        private Double latitude;
        private Double longitude;
    }
}