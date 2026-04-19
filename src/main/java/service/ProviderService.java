package com.theguy.app.service;

import com.theguy.app.dto.ProviderRegistrationDTO;
import com.theguy.app.dto.ProviderResponseDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.Service;
import com.theguy.app.entity.User;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ServiceRepository;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {
    
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    
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
        
        // Add services
        for (ProviderRegistrationDTO.ServiceDTO serviceDTO : dto.getServices()) {
            Service service = new Service();
            service.setProvider(savedProvider);
            service.setCategory(serviceDTO.getCategory());
            service.setTitle(serviceDTO.getTitle());
            service.setDescription(serviceDTO.getDescription());
            service.setPricingType(serviceDTO.getPricingType());
            service.setBasePrice(serviceDTO.getBasePrice());
            service.setActive(true);
            serviceRepository.save(service);
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
    public ProviderResponseDTO mapToResponseDTO(Provider provider) {
        return ProviderResponseDTO.builder()
            .id(provider.getId())
            .fullName(provider.getUser().getFullName())
            .phoneNumber(provider.getUser().getPhoneNumber())
            .bio(provider.getBio())
            .profileImageUrl(provider.getProfileImageUrl())
            .verificationLevel(provider.getVerificationLevel().name())
            .ratingAvg(provider.getRatingAvg())
            .totalReviews(provider.getTotalReviews())
            .jobsCompleted(provider.getJobsCompleted())
            .jobsCancelled(provider.getJobsCancelled())
            .responseRate(provider.getResponseRate())
            .repeatClientsPercentage(provider.getRepeatClientsPercentage())
            .online(provider.isOnline())
            .services(serviceRepository.findByProviderId(provider.getId()).stream()
                .map(service -> ProviderResponseDTO.ServiceDTO.builder()
                    .id(service.getId())
                    .category(service.getCategory())
                    .title(service.getTitle())
                    .pricingType(service.getPricingType())
                    .basePrice(service.getBasePrice())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }
}