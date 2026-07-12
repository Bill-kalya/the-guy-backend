package com.theguy.app.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RiskScoreDTO {
    private UUID userId;
    private String userType;
    private Integer score;
    private String riskLevel;
    private String factors;
    private String recommendations;
    private LocalDateTime calculatedAt;
    private LocalDateTime expiresAt;
}

