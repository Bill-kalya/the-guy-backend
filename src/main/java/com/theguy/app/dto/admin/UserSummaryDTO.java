package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryDTO {
    private Long totalUsers;
    private Long totalCustomers;
    private Long totalProviders;
    private Long totalAdmins;
    private Double verifiedPercentage;
}
