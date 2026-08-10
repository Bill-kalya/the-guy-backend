package com.theguy.app.dto;

import com.theguy.app.enums.ProviderBadge;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class NearbyProviderDTO {
    private UUID id;
    private String name;
    private String category;
    private String profileImageUrl;
    private Double latitude;
    private Double longitude;
    private Double distance;  // Distance in meters
    private Double serviceQualityScore; // 0-100
    private ProviderBadge badge; // Reputation badge derived from SQS
    private Double priceEstimate; // KES (backward-compatible "from" price)
    private BigDecimal minPrice; // KES
    private BigDecimal maxPrice; // KES
    private BigDecimal callOutFee; // KES
    private Double searchScore; // Blended ranking score 0-100
    private Boolean isOnline;
    private String verificationLevel;
    private Double rating;
    private Integer jobsCompleted;
    private Integer etaMinutes; // Estimated arrival time
}