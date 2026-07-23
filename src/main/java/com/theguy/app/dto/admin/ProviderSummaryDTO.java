package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderSummaryDTO {
    private Long totalProviders;
    private Long onlineNow;
    private Long pendingVerification;
    private Double avgRating;
}
