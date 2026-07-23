package com.theguy.app.entity;

import com.theguy.app.enums.VerificationDocumentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_documents", indexes = {
    @Index(name = "idx_verification_provider", columnList = "provider_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class VerificationDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationDocumentType documentType;

    @Column(nullable = false)
    private String imageUrl;

    @Column(name = "public_id")
    private String publicId;

    @Enumerated(EnumType.STRING)
    private VerificationDocumentStatus status = VerificationDocumentStatus.PENDING;

    private String rejectionReason;

    private LocalDateTime reviewedAt;

    private UUID reviewedBy;

    public enum VerificationDocumentStatus {
        PENDING, APPROVED, REJECTED, DELETED
    }
}
