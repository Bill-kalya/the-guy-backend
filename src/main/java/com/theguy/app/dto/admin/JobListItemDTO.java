package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class JobListItemDTO {
    private UUID id;
    private String serviceCategory;
    private String description;
    private String status;
    private String urgency;
    private String customerName;
    private String customerEmail;
    private String providerName;
    private String providerEmail;
    private Double finalPrice;
    private Double priceEstimateMin;
    private Double priceEstimateMax;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
