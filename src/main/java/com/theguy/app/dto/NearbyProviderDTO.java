package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class NearbyProviderDTO {
    private UUID id;
    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    private Double distance;  // Distance in meters
    private Double serviceQualityScore; // 0-100
    private Double priceEstimate; // KES
    private Boolean isOnline;
    private String verificationLevel;
    private Double rating;
    private Integer jobsCompleted;
    private Integer etaMinutes; // Estimated arrival time
}