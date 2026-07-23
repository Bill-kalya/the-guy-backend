package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserListItemDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String role;
    private boolean isVerified;
    private String avatarUrl;
    private Integer riskScore;
    private String riskLevel;
    private LocalDateTime createdAt;
}
