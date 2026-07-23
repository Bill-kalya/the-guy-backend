package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserDetailDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String role;
    private boolean isVerified;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Integer riskScore;
    private String riskLevel;
    private String riskFactors;
    private Long completedJobsCount;
    private Long disputesCount;
}
