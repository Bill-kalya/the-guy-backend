package com.theguy.app.service;

import com.theguy.app.dto.admin.VerificationDocumentAdminDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.VerificationDocument;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminVerificationService {

    private final VerificationDocumentRepository verificationDocumentRepository;
    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public Page<VerificationDocumentAdminDTO> getPendingDocuments(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return verificationDocumentRepository
            .findByStatusOrderByCreatedAtAsc(VerificationDocument.VerificationDocumentStatus.PENDING, pageable)
            .map(this::toDTO);
    }

    @Transactional
    public void approve(UUID documentId, UUID adminId) {
        VerificationDocument doc = getPendingDocument(documentId);
        doc.setStatus(VerificationDocument.VerificationDocumentStatus.APPROVED);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewedBy(adminId);
        verificationDocumentRepository.save(doc);

        Provider provider = doc.getProvider();
        if (provider.getVerificationLevel() == null
                || provider.getVerificationLevel().getLevel() < VerificationLevel.ID_VERIFIED.getLevel()) {
            provider.setVerificationLevel(VerificationLevel.ID_VERIFIED);
            providerRepository.save(provider);
        }
        log.info("Admin {} approved verification document {}", adminId, documentId);
    }

    @Transactional
    public void reject(UUID documentId, UUID adminId, String reason) {
        VerificationDocument doc = getPendingDocument(documentId);
        doc.setStatus(VerificationDocument.VerificationDocumentStatus.REJECTED);
        doc.setRejectionReason(reason);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewedBy(adminId);
        verificationDocumentRepository.save(doc);
        log.info("Admin {} rejected verification document {} reason: {}", adminId, documentId, reason);
    }

    private VerificationDocument getPendingDocument(UUID documentId) {
        VerificationDocument doc = verificationDocumentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Verification document not found"));
        if (doc.getStatus() != VerificationDocument.VerificationDocumentStatus.PENDING) {
            throw new IllegalStateException("Document already reviewed");
        }
        return doc;
    }

    private VerificationDocumentAdminDTO toDTO(VerificationDocument doc) {
        Provider provider = doc.getProvider();
        return VerificationDocumentAdminDTO.builder()
            .id(doc.getId())
            .documentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null)
            .imageUrl(doc.getImageUrl())
            .status(doc.getStatus() != null ? doc.getStatus().name() : null)
            .rejectionReason(doc.getRejectionReason())
            .createdAt(doc.getCreatedAt())
            .providerId(provider.getId())
            .providerName(provider.getUser() != null ? provider.getUser().getFullName() : "Unknown")
            .providerEmail(provider.getUser() != null ? provider.getUser().getEmail() : "")
            .verificationLevel(provider.getVerificationLevel() != null
                ? provider.getVerificationLevel().name() : "NONE")
            .build();
    }
}
