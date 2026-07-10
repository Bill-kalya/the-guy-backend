package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.NearbyProviderDTO;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.service.LocationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Update provider location (called every 3-5 seconds by provider app)
     */
    @PostMapping("/update")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @RequestBody LocationUpdateRequest request,
            Authentication authentication) {

        String userId = authentication.getName();
        UUID providerId = UUID.fromString(userId);

        locationService.updateLocation(
            providerId,
            request.getLatitude(),
            request.getLongitude()
        );

        return ResponseEntity.ok(ApiResponse.success("Location updated", null));
    }

    /**
     * Get nearby providers (customer discovery)
     */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyProviderDTO>>> getNearbyProviders(
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lng,
            @RequestParam(defaultValue = "5000") @Min(100) @Max(50000) double radius,
            @RequestParam(required = false) String category) {

        List<NearbyProviderDTO> providers = locationService.findNearbyProviders(
            lat, lng, radius, category
        );

        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    /**
     * Get a specific provider's location
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<ProviderLocation>> getProviderLocation(
            @PathVariable UUID providerId) {

        ProviderLocation location = locationService.getProviderLocation(providerId);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(location));
    }

    @Data
    public static class LocationUpdateRequest {
        @Min(-90) @Max(90)
        private Double latitude;

        @Min(-180) @Max(180)
        private Double longitude;
    }
}