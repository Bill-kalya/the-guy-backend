package com.theguy.app.dto;

import com.theguy.app.enums.PricingType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProviderResponseDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String bio;
    private String profileImageUrl;
    private String categoryId;
    private String verificationLevel;
    private Double ratingAvg;
    private Integer totalReviews;
    private Integer jobsCompleted;
    private Integer jobsCancelled;
    private Double responseRate;
    private Double repeatClientsPercentage;
    private Boolean isOnline;
    private List<String> portfolioImageUrls;
    private List<ServiceDTO> services;
    
    // SQS fields
    private Double serviceQualityScore;
    private Integer reviewCount;
    private ScoreBreakdown breakdown;
    
    @Data
    @Builder
    public static class ServiceDTO {
        private UUID id;
        private String category;
        private String title;
        private PricingType pricingType;
        private BigDecimal basePrice;
    }
    
    @Data
    @Builder
    public static class ScoreBreakdown {
        private Double professionalism;
        private Double communication;
        private Double timeliness;
        private Double workQuality;
        private Double reliability;
        private Double courtesy;
        private Double value;
    }
}