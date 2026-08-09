package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class NearbyJobDTO {
    private UUID id;
    private String customerId;
    private String customerName;
    private String customerPhone;
    private String category;
    private String description;
    private double distance;
    private double price;
    private String status;
    private String urgency;
    private String requestedAt;
    private double pickupLat;
    private double pickupLng;
    private boolean hasResponded;
}
