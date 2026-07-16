package com.theguy.app.service;

import com.theguy.app.dto.NearbyProviderDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final ProviderLocationRepository locationRepository;
    private final ProviderRepository providerRepository;

    /**
     * Update provider location (called by provider app)
     */
    @Transactional
    public void updateLocation(UUID providerId, double latitude, double longitude) {
        ProviderLocation location = locationRepository.findByProviderId(providerId)
            .orElse(new ProviderLocation());

        location.setProviderId(providerId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setUpdatedAt(LocalDateTime.now());

        locationRepository.save(location);

        // Update provider's online status if they're sending location
        providerRepository.findById(providerId).ifPresent(provider -> {
            if (!provider.isOnline()) {
                provider.setOnline(true);
                provider.setLastActiveAt(LocalDateTime.now());
                providerRepository.save(provider);
            }
        });

        log.debug("Updated location for provider: {} at ({}, {})", providerId, latitude, longitude);
    }

    /**
     * Get nearby providers with distance and quality scores
     */
    @Transactional(readOnly = true)
    public List<NearbyProviderDTO> findNearbyProviders(
            double lat,
            double lng,
            double radiusMeters,
            String category) {

        // Get bounding box to reduce database scan
        LocationUtils.BoundingBox bbox = LocationUtils.getBoundingBox(lat, lng, radiusMeters);

        // Find nearby provider locations (optionally filtered by category)
        List<ProviderLocation> locations;
        if (category != null && !category.isBlank()) {
            locations = locationRepository.findNearbyProvidersByCategory(
                lat, lng, radiusMeters,
                bbox.minLat, bbox.maxLat,
                bbox.minLng, bbox.maxLng,
                category
            );
        } else {
            locations = locationRepository.findNearbyProviders(
                lat, lng, radiusMeters,
                bbox.minLat, bbox.maxLat,
                bbox.minLng, bbox.maxLng
            );
        }

        // Get provider details
        List<UUID> providerIds = locations.stream()
            .map(ProviderLocation::getProviderId)
            .collect(Collectors.toList());

        List<Provider> providers = providerRepository.findAllById(providerIds);

        // Map providers to response DTOs
        return providers.stream()
            .map(provider -> {
                ProviderLocation location = locations.stream()
                    .filter(l -> l.getProviderId().equals(provider.getId()))
                    .findFirst()
                    .orElse(null);

                if (location == null) return null;

                double distance = LocationUtils.calculateDistance(
                    lat, lng,
                    location.getLatitude(),
                    location.getLongitude()
                );

                double qualityScore = LocationUtils.calculateQualityScore(
                    provider.getRatingAvg(),
                    provider.getJobsCompleted(),
                    provider.getResponseRate()
                );

                int etaMinutes = LocationUtils.calculateETA(distance, 30.0);

                return NearbyProviderDTO.builder()
                    .id(provider.getId())
                    .name(provider.getUser().getFullName())
                    .category(provider.getServices().stream()
                        .findFirst()
                        .map(s -> s.getCategory())
                        .orElse("Unknown"))
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .distance(distance)
                    .serviceQualityScore(qualityScore)
                    .priceEstimate(calculatePriceEstimate(provider))
                    .isOnline(provider.isOnline())
                    .verificationLevel(provider.getVerificationLevel().name())
                    .rating(provider.getRatingAvg())
                    .jobsCompleted(provider.getJobsCompleted())
                    .etaMinutes(etaMinutes)
                    .build();
            })
            .filter(dto -> dto != null)
            .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
            .collect(Collectors.toList());
    }

    private Double calculatePriceEstimate(Provider provider) {
        // Base estimate from provider's services
        java.math.BigDecimal basePrice = provider.getServices().stream()
            .findFirst()
            .map(service -> service.getBasePrice())
            .orElse(java.math.BigDecimal.valueOf(500.0));

        // Apply rating multiplier
        double ratingMultiplier = 1.0;
        if (provider.getRatingAvg() >= 4.5) ratingMultiplier = 1.2;
        else if (provider.getRatingAvg() >= 4.0) ratingMultiplier = 1.0;
        else if (provider.getRatingAvg() >= 3.5) ratingMultiplier = 0.9;
        else ratingMultiplier = 0.8;

        return basePrice.doubleValue() * ratingMultiplier;
    }

    /**
     * Get provider's current location
     */
    @Transactional(readOnly = true)
    public ProviderLocation getProviderLocation(UUID providerId) {
        return locationRepository.findByProviderId(providerId)
            .orElse(null);
    }

    /**
     * Get all online provider locations (for WebSocket broadcast)
     */
    @Transactional(readOnly = true)
    public List<ProviderLocation> getAllOnlineProviderLocations() {
        List<Provider> onlineProviders = providerRepository.findByIsOnlineTrue();
        List<UUID> providerIds = onlineProviders.stream()
            .map(Provider::getId)
            .collect(Collectors.toList());

        return locationRepository.findByProviderIds(providerIds);
    }
}