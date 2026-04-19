package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProviderResponseDTO {
    private UUID id;
    private String fullName;
    private String phoneNumber;
    private String bio;
    private String profileImageUrl;
    private String verificationLevel;
    private Double ratingAvg;
    private Integer totalReviews;
    private Integer jobsCompleted;
    private Integer jobsCancelled;
    private Double responseRate;
    private Double repeatClientsPercentage;
    private boolean isOnline;
    private List<ServiceDTO> services;
    
    @Data
    @Builder
    public static class ServiceDTO {
        private UUID id;
        private String category;
        private String title;
        private String pricingType;
        private Double basePrice;
    }
}