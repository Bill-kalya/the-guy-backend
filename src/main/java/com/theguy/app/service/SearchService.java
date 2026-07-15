package com.theguy.app.service;

import com.theguy.app.dto.SearchProviderItem;
import com.theguy.app.dto.SearchProvidersResponse;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ServiceRepository;
import com.theguy.app.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public SearchProvidersResponse searchProviders(
            String query,
            double lat,
            double lng,
            double radiusMeters,
            int page,
            int size) {

        String normalizedQuery = normalizeQuery(query);
        List<String> categories = resolveCategories(normalizedQuery);

        if (categories.isEmpty()) {
            return SearchProvidersResponse.builder()
                .query(query)
                .totalResults(0)
                .providers(List.of())
                .build();
        }

        LocationUtils.BoundingBox bbox = LocationUtils.getBoundingBox(lat, lng, radiusMeters);
        List<ProviderLocation> locations = providerLocationRepository.findNearbyProviders(
            lat, lng, radiusMeters,
            bbox.minLat, bbox.maxLat,
            bbox.minLng, bbox.maxLng
        );

        List<UUID> providerIds = locations.stream()
            .map(ProviderLocation::getProviderId)
            .distinct()
            .toList();

        List<Provider> providers = providerRepository.findAllById(providerIds);

        List<SearchProviderItem> ranked = providers.stream()
            .filter(provider -> provider.getServices() != null && provider.getServices().stream().anyMatch(service ->
                categories.contains(normalizeQuery(service.getCategory()))))
            .map(provider -> {
                ProviderLocation location = locations.stream()
                    .filter(item -> item.getProviderId().equals(provider.getId()))
                    .findFirst()
                    .orElse(null);

                if (location == null) {
                    return null;
                }

                double distance = LocationUtils.calculateDistance(lat, lng, location.getLatitude(), location.getLongitude());
                double distanceScore = Math.max(0, 100 - (distance / 1000.0) * 10);
                double responseScore = Math.max(0, provider.getResponseRate() * 100);
                double completionScore = Math.min(100, (provider.getJobsCompleted() / 1000.0) * 100);
                double verificationScore = switch (provider.getVerificationLevel()) {
                    case BASIC -> 70;
                    case ID_VERIFIED -> 85;
                    case BUSINESS -> 100;
                    default -> 40;
                };
                double serviceQualityScore = LocationUtils.calculateQualityScore(
                    provider.getRatingAvg(),
                    provider.getJobsCompleted(),
                    provider.getResponseRate()
                );

                double rank = (0.40 * distanceScore)
                    + (0.30 * serviceQualityScore)
                    + (0.15 * responseScore)
                    + (0.10 * completionScore)
                    + (0.05 * verificationScore);

                return SearchProviderItem.builder()
                    .id(provider.getId())
                    .businessName(provider.getUser().getFullName())
                    .distance(distance)
                    .etaMinutes(LocationUtils.calculateETA(distance, 30.0))
                    .serviceQualityScore(serviceQualityScore)
                    .verified(provider.getVerificationLevel() != null && provider.getVerificationLevel() != com.theguy.app.enums.VerificationLevel.NONE)
                    .rating(provider.getRatingAvg())
                    .completedJobs(provider.getJobsCompleted())
                    .build();
            })
            .filter(item -> item != null)
            .sorted(Comparator.comparingDouble(SearchProviderItem::getServiceQualityScore).reversed())
            .toList();

        int fromIndex = Math.max(0, page * size);
        int toIndex = Math.min(ranked.size(), fromIndex + size);

        return SearchProvidersResponse.builder()
            .query(query)
            .totalResults(ranked.size())
            .providers(ranked.subList(fromIndex, toIndex))
            .build();
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        return serviceRepository.findAllCategories().stream()
            .filter(category -> normalizeQuery(category).startsWith(normalizedQuery))
            .map(this::toDisplayName)
            .sorted()
            .limit(5)
            .toList();
    }

    private List<String> resolveCategories(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = normalizeQuery(query);
        return serviceRepository.findAllCategories().stream()
            .filter(category -> normalizeQuery(category).equals(normalized))
            .collect(Collectors.toList());
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ENGLISH);
    }

    private String toDisplayName(String value) {
        return value == null ? "" : value;
    }
}
