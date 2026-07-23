package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderFinancialsDTO {
    private Double pendingBalance;
    private Double availableBalance;
    private String currency;
    private Double totalEarnings;
    private Double totalWithdrawals;
    private Long pendingPayoutCount;
}
