package com.theguy.app.service;

import com.theguy.app.entity.Provider;
import com.theguy.app.repository.PortfolioImageRepository;
import com.theguy.app.repository.VerificationDocumentRepository;
import com.theguy.app.entity.VerificationDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderProfileCompletionService {

    private final PortfolioImageRepository portfolioImageRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> calculateCompletion(Provider provider) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> checks = new LinkedHashMap<>();
        int totalScore = 0;

        // Profile Photo (20 points)
        boolean hasProfilePhoto = provider.getProfileImageUrl() != null && !provider.getProfileImageUrl().isBlank();
        checks.put("profilePhoto", Map.of(
            "label", "Profile Photo",
            "completed", hasProfilePhoto,
            "points", 20,
            "maxPoints", 20
        ));
        if (hasProfilePhoto) totalScore += 20;

        // Portfolio Photos (30 points) — 3 minimum
        long portfolioCount = portfolioImageRepository.countByProviderIdAndIsActiveTrue(provider.getId());
        boolean hasPortfolio = portfolioCount >= 3;
        checks.put("portfolioPhotos", Map.of(
            "label", "Portfolio Photos",
            "completed", hasPortfolio,
            "current", portfolioCount,
            "required", 3,
            "points", hasPortfolio ? 30 : (int)(portfolioCount * 10),
            "maxPoints", 30
        ));
        totalScore += hasPortfolio ? 30 : (int)(portfolioCount * 10);

        // Verification Documents (30 points) — 1 minimum
        long docCount = verificationDocumentRepository.countByProviderIdAndStatus(
            provider.getId(), VerificationDocument.VerificationDocumentStatus.APPROVED);
        boolean hasDocs = docCount >= 1;
        checks.put("verificationDocuments", Map.of(
            "label", "Verification Documents",
            "completed", hasDocs,
            "current", docCount,
            "required", 1,
            "points", hasDocs ? 30 : (int)(docCount * 30),
            "maxPoints", 30
        ));
        totalScore += hasDocs ? 30 : (int)(docCount * 30);

        // Bio (10 points)
        boolean hasBio = provider.getBio() != null && !provider.getBio().isBlank() && provider.getBio().length() >= 20;
        checks.put("bio", Map.of(
            "label", "Bio",
            "completed", hasBio,
            "points", hasBio ? 10 : 0,
            "maxPoints", 10
        ));
        if (hasBio) totalScore += 10;

        // Category (10 points) — always set during registration
        boolean hasCategory = provider.getCategoryId() != null && !provider.getCategoryId().isBlank();
        checks.put("category", Map.of(
            "label", "Service Category",
            "completed", hasCategory,
            "points", hasCategory ? 10 : 0,
            "maxPoints", 10
        ));
        if (hasCategory) totalScore += 10;

        result.put("score", totalScore);
        result.put("checks", checks);
        result.put("label", getCompletionLabel(totalScore));

        return result;
    }

    private String getCompletionLabel(int score) {
        if (score >= 100) return "Complete";
        if (score >= 80) return "Almost there";
        if (score >= 50) return "Halfway";
        if (score >= 20) return "Getting started";
        return "Just started";
    }
}
