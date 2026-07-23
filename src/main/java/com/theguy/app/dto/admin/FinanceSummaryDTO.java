package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinanceSummaryDTO {
    private Double totalGMV;
    private Double totalRevenue;
    private Double totalEscrow;
    private Double totalTaxLiability;
    private Double pendingPayoutsTotal;
    private Long openDisputesTotal;
    private Double refundExposure;
    private Long failedPayments;
}
