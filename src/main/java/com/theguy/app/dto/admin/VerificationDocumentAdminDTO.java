package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VerificationDocumentAdminDTO {
    private UUID id;
    private String documentType;
    private String imageUrl;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private UUID providerId;
    private String providerName;
    private String providerEmail;
    private String verificationLevel;
}
