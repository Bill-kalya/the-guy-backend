package com.theguy.app.service;

import com.theguy.app.dto.ProviderRegistrationDTO;
import com.theguy.app.dto.ProviderResponseDTO;
import com.theguy.app.entity.PortfolioImage;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.User;
import com.theguy.app.entity.VerificationDocument;
import com.theguy.app.enums.VerificationDocumentType;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.theguy.app.service.ProviderStatisticsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {
    
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ProviderStatisticsService providerStatisticsService;
    private final WalletService walletService;
    
    @Transactional
    public Provider registerProvider(User user, ProviderRegistrationDTO dto) {
        log.info("Registering new provider for user: {}", user.getId());
        
        if (providerRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalStateException("User is already registered as a provider");
        }
        
        Provider provider = new Provider();
        provider.setUser(user);
        provider.setBio(dto.getBio());
        provider.setCategoryId(dto.getCategoryId());
        provider.setProfileImageUrl(dto.getProfileImageUrl());
        provider.setVerificationLevel(VerificationLevel.BASIC);
        provider.setOnline(true);
        provider.setDynamicPriceMultiplier(1.0);
        provider.setResponseRate(1.0);
        
        Provider savedProvider = providerRepository.save(provider);
        
        // Portfolio images
        if (dto.getPortfolioImageUrls() != null) {
            for (int i = 0; i < dto.getPortfolioImageUrls().size(); i++) {
                PortfolioImage img = new PortfolioImage();
                img.setProvider(savedProvider);
                img.setImageUrl(dto.getPortfolioImageUrls().get(i));
                img.setSortOrder(i);
                img.setIsActive(true);
                savedProvider.getPortfolioImages().add(img);
            }
        }
        
        // Verification documents
        if (dto.getVerificationDocuments() != null) {
            for (ProviderRegistrationDTO.VerificationDocDTO docDto : dto.getVerificationDocuments()) {
                VerificationDocument doc = new VerificationDocument();
                doc.setProvider(savedProvider);
                doc.setDocumentType(VerificationDocumentType.valueOf(docDto.getDocumentType()));
                doc.setImageUrl(docDto.getImageUrl());
                doc.setStatus(VerificationDocument.VerificationDocumentStatus.PENDING);
                savedProvider.getVerificationDocuments().add(doc);
            }
        }
        
        providerRepository.save(savedProvider);
        
        user.setRole(com.theguy.app.enums.Role.PROVIDER);
        userRepository.save(user);
        
        if (dto.getLatitude() != null && dto.getLongitude() != null) {
            ProviderLocation location = new ProviderLocation();
            location.setProviderId(savedProvider.getId());
            location.setLatitude(dto.getLatitude());
            location.setLongitude(dto.getLongitude());
            providerLocationRepository.save(location);
            log.info("Saved initial location for provider {}: ({}, {})",
                savedProvider.getId(), dto.getLatitude(), dto.getLongitude());
        }

        log.info("Provider registered successfully with ID: {} category: {}", savedProvider.getId(), dto.getCategoryId());
        return savedProvider;
    }
    
    @Transactional
    public void updateOnlineStatus(UUID providerId, boolean isOnline) {
        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        provider.setOnline(isOnline);
        provider.setLastActiveAt(LocalDateTime.now());
        providerRepository.save(provider);
        
        log.info("Provider {} online status updated to: {}", providerId, isOnline);
    }
    
    @Transactional
    public void updateLocation(UUID providerId, double latitude, double longitude) {
        ProviderLocation location = providerLocationRepository.findByProviderId(providerId)
            .orElse(new ProviderLocation());
        
        location.setProviderId(providerId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setUpdatedAt(LocalDateTime.now());
        
        providerLocationRepository.save(location);
        log.info("Provider {} location updated to ({}, {})", providerId, latitude, longitude);
    }
    
    @Transactional
    public void updateVerificationLevel(UUID providerId, VerificationLevel level) {
        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        provider.setVerificationLevel(level);
        providerRepository.save(provider);
        
        log.info("Provider {} verification level updated to: {}", providerId, level);
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getEarnings(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Provider provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        Long completedJobs = jobRepository.countCompletedByProvider(provider.getId());
        Double totalEarnings = jobRepository.getTotalEarningsByProvider(provider.getId());

        double pendingBalance = 0.0;
        double availableBalance = 0.0;
        String currency = "KES";

        try {
            var wallet = walletService.getWallet(provider.getId());
            pendingBalance = wallet.getPendingBalance();
            availableBalance = wallet.getAvailableBalance();
            currency = wallet.getCurrency();
        } catch (Exception e) {
            log.warn("Could not load wallet for provider {}: {}", provider.getId(), e.getMessage());
        }

        Map<String, Object> earnings = new HashMap<>();
        earnings.put("totalEarnings", totalEarnings != null ? totalEarnings : 0.0);
        earnings.put("jobsCompleted", completedJobs != null ? completedJobs : 0);
        earnings.put("pendingBalance", pendingBalance);
        earnings.put("availableBalance", availableBalance);
        earnings.put("totalBalance", pendingBalance + availableBalance);
        earnings.put("currency", currency);

        return earnings;
    }

    @Transactional(readOnly = true)
    public ProviderResponseDTO mapToResponseDTO(Provider provider) {
        var statsOpt = providerStatisticsService.getStatistics(provider.getId());
        
        ProviderResponseDTO.ScoreBreakdown breakdown = null;
        Double sqs = null;
        Integer reviewCount = null;
        
        if (statsOpt.isPresent()) {
            var stats = statsOpt.get();
            sqs = stats.getSqs();
            reviewCount = stats.getReviewCount();
            
            breakdown = ProviderResponseDTO.ScoreBreakdown.builder()
                .professionalism(stats.getProfessionalismScore())
                .communication(stats.getCommunicationScore())
                .timeliness(stats.getTimelinessScore())
                .workQuality(stats.getWorkQualityScore())
                .reliability(stats.getReliabilityScore())
                .courtesy(stats.getCourtesyScore())
                .value(stats.getValueScore())
                .build();
        }
        
        var portfolioUrls = provider.getPortfolioImages() != null
            ? provider.getPortfolioImages().stream()
                .filter(img -> img.getIsActive() != null && img.getIsActive())
                .sorted((a, b) -> Integer.compare(
                    a.getSortOrder() != null ? a.getSortOrder() : 0,
                    b.getSortOrder() != null ? b.getSortOrder() : 0))
                .map(PortfolioImage::getImageUrl)
                .collect(Collectors.toList())
            : java.util.List.<String>of();
        
        return ProviderResponseDTO.builder()
            .id(provider.getId())
            .fullName(provider.getUser() != null ? provider.getUser().getFullName() : "Unknown")
            .email(provider.getUser() != null ? provider.getUser().getEmail() : "")
            .bio(provider.getBio())
            .profileImageUrl(provider.getProfileImageUrl())
            .categoryId(provider.getCategoryId())
            .verificationLevel(provider.getVerificationLevel() != null ? provider.getVerificationLevel().name() : "BASIC")
            .ratingAvg(provider.getRatingAvg())
            .totalReviews(provider.getTotalReviews())
            .jobsCompleted(provider.getJobsCompleted())
            .jobsCancelled(provider.getJobsCancelled())
            .responseRate(provider.getResponseRate())
            .repeatClientsPercentage(provider.getRepeatClientsPercentage())
            .isOnline(provider.isOnline())
            .portfolioImageUrls(portfolioUrls)
            .serviceQualityScore(sqs)
            .reviewCount(reviewCount)
            .breakdown(breakdown)
            .services(provider.getServices() != null
                ? provider.getServices().stream()
                    .map(s -> ProviderResponseDTO.ServiceDTO.builder()
                        .id(s.getId())
                        .category(s.getCategory())
                        .title(s.getTitle())
                        .pricingType(s.getPricingType())
                        .basePrice(s.getBasePrice())
                        .build())
                    .collect(Collectors.toList())
                : java.util.List.of())
            .build();
    }
}
