package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProviderListItemDTO {
    private UUID id;
    private UUID userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String bio;
    private String profileImageUrl;
    private String verificationLevel;
    private boolean isOnline;
    private Double ratingAvg;
    private Integer totalReviews;
    private Integer jobsCompleted;
    private Integer jobsCancelled;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
