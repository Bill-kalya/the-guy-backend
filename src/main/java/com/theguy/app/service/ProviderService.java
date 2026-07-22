package com.theguy.app.service;

import com.theguy.app.dto.ProviderRegistrationDTO;
import com.theguy.app.dto.ProviderResponseDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.User;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ServiceRepository;
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

// Import for ProviderStatistics
import com.theguy.app.service.ProviderStatisticsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {
    
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ProviderStatisticsService providerStatisticsService;
    private final WalletService walletService;
    
    @Transactional
    public Provider registerProvider(User user, ProviderRegistrationDTO dto) {
        log.info("Registering new provider for user: {}", user.getId());
        
        // Check if user is already a provider
        if (providerRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalStateException("User is already registered as a provider");
        }
        
        // Create provider profile
        Provider provider = new Provider();
        provider.setUser(user);
        provider.setBio(dto.getBio());
        provider.setProfileImageUrl(dto.getProfileImageUrl());
        provider.setVerificationLevel(VerificationLevel.BASIC);
        provider.setOnline(false);
        provider.setDynamicPriceMultiplier(1.0);
        provider.setResponseRate(1.0);
        
        Provider savedProvider = providerRepository.save(provider);
        
        // Escalate user role to PROVIDER
        user.setRole(com.theguy.app.enums.Role.PROVIDER);
        userRepository.save(user);
        
        // Add services
        for (ProviderRegistrationDTO.ServiceDTO serviceDTO : dto.getServices()) {
            com.theguy.app.entity.Service service = new com.theguy.app.entity.Service();
            service.setProvider(savedProvider);
            service.setCategory(serviceDTO.getCategory());
            service.setTitle(serviceDTO.getTitle());
            service.setDescription(serviceDTO.getDescription());
            service.setPricingType(serviceDTO.getPricingType());
            service.setBasePrice(serviceDTO.getBasePrice());
            service.setIsActive(true);
            serviceRepository.save(service);
        }
        
        // Save location if provided
        if (dto.getLatitude() != null && dto.getLongitude() != null) {
            ProviderLocation location = new ProviderLocation();
            location.setProviderId(savedProvider.getId());
            location.setLatitude(dto.getLatitude());
            location.setLongitude(dto.getLongitude());
            providerLocationRepository.save(location);
            log.info("Saved initial location for provider {}: ({}, {})",
                savedProvider.getId(), dto.getLatitude(), dto.getLongitude());
        }

        log.info("Provider registered successfully with ID: {}", savedProvider.getId());
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

        // Get wallet balances
        var wallet = walletService.getWallet(provider.getId());

        Map<String, Object> earnings = new HashMap<>();
        earnings.put("totalEarnings", totalEarnings != null ? totalEarnings : 0.0);
        earnings.put("jobsCompleted", completedJobs != null ? completedJobs : 0);
        earnings.put("pendingBalance", wallet.getPendingBalance());
        earnings.put("availableBalance", wallet.getAvailableBalance());
        earnings.put("totalBalance", wallet.getPendingBalance() + wallet.getAvailableBalance());
        earnings.put("currency", wallet.getCurrency());

        return earnings;
    }

    @Transactional(readOnly = true)
    public ProviderResponseDTO mapToResponseDTO(Provider provider) {
        // Get provider statistics
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
        
        return ProviderResponseDTO.builder()
            .id(provider.getId())
            .fullName(provider.getUser() != null ? provider.getUser().getFullName() : "Unknown")
            .email(provider.getUser() != null ? provider.getUser().getEmail() : "")
            .bio(provider.getBio())
            .profileImageUrl(provider.getProfileImageUrl())
            .verificationLevel(provider.getVerificationLevel() != null ? provider.getVerificationLevel().name() : "BASIC")
            .ratingAvg(provider.getRatingAvg())
            .totalReviews(provider.getTotalReviews())
            .jobsCompleted(provider.getJobsCompleted())
            .jobsCancelled(provider.getJobsCancelled())
            .responseRate(provider.getResponseRate())
            .repeatClientsPercentage(provider.getRepeatClientsPercentage())
            .isOnline(provider.isOnline())
            .serviceQualityScore(sqs)
            .reviewCount(reviewCount)
            .breakdown(breakdown)
            .services(serviceRepository.findByProviderId(provider.getId()).stream()
                .map(s -> ProviderResponseDTO.ServiceDTO.builder()
                    .id(s.getId())
                    .category(s.getCategory())
                    .title(s.getTitle())
                    .pricingType(s.getPricingType())
                    .basePrice(s.getBasePrice())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }
}