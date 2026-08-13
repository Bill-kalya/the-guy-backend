package com.theguy.app.service;

import com.theguy.app.dto.NearbyProviderDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.enums.ProviderBadge;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    /** How long a provider may be silent before they're considered offline. */
    private static final java.time.Duration ONLINE_HEARTBEAT_GRACE = java.time.Duration.ofMinutes(2);

    /** Jobs older than this in a non-terminal state are treated as abandoned. */
    private static final java.time.Duration ACTIVE_JOB_GRACE = java.time.Duration.ofHours(24);

    private final ProviderLocationRepository locationRepository;
    private final ProviderRepository providerRepository;
    private final JobRepository jobRepository;

    @Transactional
    public void updateLocation(UUID providerId, double latitude, double longitude, Double heading, Double speed) {
        ProviderLocation location = locationRepository.findByProviderId(providerId)
            .orElse(new ProviderLocation());

        location.setProviderId(providerId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        if (heading != null) location.setHeading(heading);
        if (speed != null) location.setSpeed(speed);
        location.setUpdatedAt(LocalDateTime.now());

        locationRepository.save(location);

        // Heartbeat: refresh last-seen on every location update. A provider only
        // becomes online via the explicit availability toggle; location pings
        // alone must not silently flip them online.
        providerRepository.updateLastActiveAt(providerId, LocalDateTime.now());

        log.debug("Updated location for provider: {} at ({}, {})", providerId, latitude, longitude);
    }

    @Transactional(readOnly = true)
    public List<NearbyProviderDTO> findNearbyProviders(
            double lat,
            double lng,
            double radiusMeters,
            String category) {

        LocationUtils.BoundingBox bbox = LocationUtils.getBoundingBox(lat, lng, radiusMeters);

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

        log.info("Nearby search: lat={}, lng={}, radius={}, category={}, found {} locations",
            lat, lng, radiusMeters, category, locations.size());

        List<UUID> providerIds = locations.stream()
            .map(ProviderLocation::getProviderId)
            .collect(Collectors.toList());

        List<Provider> providers = providerRepository.findAllById(providerIds).stream()
                .filter(Provider::isAccountClaimed)
                .collect(Collectors.toList());
        return providers.stream()
            .map(provider -> {
                ProviderLocation location = locations.stream()
                    .filter(l -> l.getProviderId().equals(provider.getId()))
                    .findFirst()
                    .orElse(null);

                if (location == null) return null;
                if (provider.getUser() == null) {
                    log.warn("Provider {} has no linked user, skipping", provider.getId());
                    return null;
                }

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

                com.theguy.app.entity.Service pricingService = findPricingService(provider);
                double priceEstimate = resolveFromPrice(pricingService);
                BigDecimal minPrice = resolvePrice(pricingService, PricingField.MIN);
                BigDecimal maxPrice = resolvePrice(pricingService, PricingField.MAX);
                BigDecimal callOutFee = resolvePrice(pricingService, PricingField.CALL_OUT);

                double searchScore = calculateSearchScore(
                    qualityScore,
                    distance,
                    provider.isOnline(),
                    priceEstimate
                );

                return NearbyProviderDTO.builder()
                    .id(provider.getId())
                    .name(provider.getUser().getFullName())
                    .category(provider.getCategoryId() != null ? provider.getCategoryId() : "Unknown")
                    .profileImageUrl(provider.getProfileImageUrl())
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .distance(distance)
                    .serviceQualityScore(qualityScore)
                    .badge(ProviderBadge.fromScore(qualityScore))
                    .priceEstimate(priceEstimate)
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .callOutFee(callOutFee)
                    .searchScore(searchScore)
                    .isOnline(provider.isOnline())
                    .verificationLevel(provider.getVerificationLevel() != null
                        ? provider.getVerificationLevel().name()
                        : "BASIC")
                    .rating(provider.getRatingAvg())
                    .jobsCompleted(provider.getJobsCompleted())
                    .etaMinutes(etaMinutes)
                    .build();
            })
            .filter(dto -> dto != null)
            .sorted(
                Comparator.comparingDouble(NearbyProviderDTO::getSearchScore).reversed()
                    .thenComparingDouble(NearbyProviderDTO::getDistance))
            .collect(Collectors.toList());
    }

    private enum PricingField { MIN, MAX, CALL_OUT }

    /**
     * Resolve the provider's active service used for pricing. Providers set
     * their own price ranges; SQS influences ranking, never pricing.
     */
    private com.theguy.app.entity.Service findPricingService(Provider provider) {
        if (provider.getServices() == null) {
            return null;
        }
        return provider.getServices().stream()
            .filter(s -> s.getIsActive() != null && s.getIsActive())
            .findFirst()
            .orElse(null);
    }

    private BigDecimal resolvePrice(com.theguy.app.entity.Service service, PricingField field) {
        if (service == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = switch (field) {
            case MIN -> service.getMinPrice();
            case MAX -> service.getMaxPrice();
            case CALL_OUT -> service.getCallOutFee();
        };
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * "From KES X" price: the provider's configured minimum, with a sensible
     * fallback when no service pricing has been set yet.
     */
    private double resolveFromPrice(com.theguy.app.entity.Service service) {
        if (service != null && service.getMinPrice() != null && service.getMinPrice().doubleValue() > 0) {
            return service.getMinPrice().doubleValue();
        }
        return 500.0;
    }

    /**
     * Blended ranking: reputation (40%) + distance (25%) + availability (20%)
     * + price competitiveness (15%). SQS drives visibility, not pricing.
     */
    private double calculateSearchScore(double qualityScore, double distance, boolean online, double fromPrice) {
        double distanceScore = Math.max(0, 100 - (distance / 1000.0) * 10);
        double availabilityScore = online ? 100.0 : 40.0;
        double priceScore = Math.max(0, 100 - Math.min(100, (fromPrice / 1000.0) * 20));

        return (0.40 * qualityScore)
            + (0.25 * distanceScore)
            + (0.20 * availabilityScore)
            + (0.15 * priceScore);
    }

    @Transactional(readOnly = true)
    public ProviderLocation getProviderLocation(UUID providerId) {
        return locationRepository.findByProviderId(providerId)
            .orElse(null);
    }

    /**
     * Authorization rule for live-location access:
     *  - the provider requesting their own location, or
     *  - a customer with an active job involving the provider.
     * (Admin is handled by the caller.)
     */
    @Transactional(readOnly = true)
    public boolean canTrack(UUID requesterUserId, UUID providerId) {
        if (providerRepository.findByUserId(requesterUserId)
                .map(p -> p.getId().equals(providerId))
                .orElse(false)) {
            return true;
        }

        return jobRepository.existsActiveJobBetween(
            requesterUserId,
            providerId,
            java.util.List.of(JobStatus.REQUESTED, JobStatus.MATCHING, JobStatus.ASSIGNED,
                    JobStatus.ON_THE_WAY, JobStatus.ARRIVED, JobStatus.IN_PROGRESS,
                    JobStatus.AWAITING_CUSTOMER_CONFIRMATION));
    }

    @Transactional(readOnly = true)
    public List<ProviderLocation> getAllOnlineProviderLocations() {
        List<Provider> onlineProviders = providerRepository.findByIsOnlineTrue();
        List<UUID> providerIds = onlineProviders.stream()
            .map(Provider::getId)
            .collect(Collectors.toList());

        return locationRepository.findByProviderIds(providerIds);
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void cleanupStaleLocations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime locationThreshold = now.minusSeconds(60);
        LocalDateTime heartbeatThreshold = now.minus(ONLINE_HEARTBEAT_GRACE);

        // Providers whose location heartbeat stopped (existing behavior), plus
        // providers flagged online who have no fresh heartbeat at all. The second
        // group catches providers who toggled online but never produced a location
        // row (e.g. no GPS fix) — the location-based sweep alone would never find
        // them because there is no row to go stale.
        java.util.Set<UUID> staleIds = new java.util.HashSet<>(
                locationRepository.findStaleProviderIds(locationThreshold));
        staleIds.addAll(providerRepository.findOnlineSinceBefore(heartbeatThreshold));

        if (staleIds.isEmpty()) return;

        List<Provider> staleProviders = providerRepository.findAllById(staleIds);

        // Only currently-online providers need cleaning; already-offline ones
        // are a no-op and would otherwise re-log on every sweep.
        List<Provider> staleOnline = staleProviders.stream()
                .filter(Provider::isOnline)
                .collect(Collectors.toList());
        if (staleOnline.isEmpty()) return;

        List<UUID> staleOnlineIds = staleOnline.stream()
                .map(Provider::getId)
                .collect(Collectors.toList());
        LocalDateTime activeJobFreshSince = now.minus(ACTIVE_JOB_GRACE);
        List<UUID> activeJobProviderIds = jobRepository.findProviderIdsWithActiveJobs(
                staleOnlineIds,
                java.util.List.of(JobStatus.REQUESTED, JobStatus.MATCHING, JobStatus.ASSIGNED,
                        JobStatus.ON_THE_WAY, JobStatus.ARRIVED, JobStatus.IN_PROGRESS,
                        JobStatus.AWAITING_CUSTOMER_CONFIRMATION),
                activeJobFreshSince);

        // Providers mid-job are kept online (they're being tracked); idle
        // providers whose heartbeat stopped are taken offline so they don't
        // stay discoverable — or counted on the dashboard — with stale state.
        // Jobs older than the grace window in a non-terminal state are treated
        // as abandoned so a stuck job can't pin a provider online forever.
        List<Provider> toOffline = staleOnline.stream()
                .filter(p -> !activeJobProviderIds.contains(p.getId()))
                .collect(Collectors.toList());

        if (toOffline.isEmpty()) return;

        for (Provider provider : toOffline) {
            provider.setOnline(false);
            provider.setLastActiveAt(now);
        }
        providerRepository.saveAll(toOffline);

        log.info("Marked {} idle providers offline due to stale heartbeat ({} active providers kept online)",
                toOffline.size(), staleOnline.size() - toOffline.size());
    }
}
