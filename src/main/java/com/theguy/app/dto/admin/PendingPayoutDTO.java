package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PendingPayoutDTO {
    private UUID payoutId;
    private UUID providerId;
    private String providerName;
    private String providerEmail;
    private Double amount;
    private String method;
    private LocalDateTime createdAt;
}
