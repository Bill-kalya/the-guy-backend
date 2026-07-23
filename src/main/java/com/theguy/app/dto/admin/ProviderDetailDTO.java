package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProviderDetailDTO {
    private UUID id;
    private UUID userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String bio;
    private String profileImageUrl;
    private String verificationLevel;
    private boolean isOnline;
    private Double ratingAvg;
    private Integer totalReviews;
    private Integer jobsCompleted;
    private Integer jobsCancelled;
    private Double responseRate;
    private Double repeatClientsPercentage;
    private Double dynamicPriceMultiplier;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private Double pendingBalance;
    private Double availableBalance;
    private String walletCurrency;
    private Integer riskScore;
    private String riskLevel;
    private Long openDisputesCount;
}
