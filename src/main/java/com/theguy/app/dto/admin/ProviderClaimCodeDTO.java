package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProviderClaimCodeDTO {
    private UUID providerId;
    private String claimCode;
    private LocalDateTime expiresAt;
}
