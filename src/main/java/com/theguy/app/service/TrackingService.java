package com.theguy.app.service;

import com.theguy.app.entity.Job;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live tracking support for an assigned job: ETA between the provider's latest
 * location heartbeat and the job pickup point, plus a straight-line route
 * polyline for map rendering. Returns raw maps (not the ApiResponse envelope)
 * so the mobile tracking engine can read {@code data['eta']} / {@code data['polyline']}
 * directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final JobRepository jobRepository;
    private final ProviderLocationRepository locationRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getEta(UUID jobId) {
        Route route = buildRoute(jobId);
        int minutes = LocationUtils.calculateETA(route.distanceMeters, route.avgSpeedKmh);
        return Map.of(
                "eta", minutes,
                "distance", Math.round(route.distanceMeters),
                "providerId", route.providerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPolyline(UUID jobId) {
        Route route = buildRoute(jobId);
        List<Map<String, Object>> points = interpolate(
                route.startLat, route.startLng, route.endLat, route.endLng, 24);
        return Map.of(
                "polyline", points,
                "providerId", route.providerId);
    }

    private Route buildRoute(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getProvider() == null) {
            throw new IllegalStateException("No provider assigned to this job yet");
        }
        if (job.getLatitude() == null || job.getLongitude() == null) {
            throw new IllegalStateException("Job has no pickup location");
        }

        UUID providerId = job.getProvider().getId();
        double destLat = job.getLatitude();
        double destLng = job.getLongitude();

        ProviderLocation loc = locationRepository.findByProviderId(providerId)
                .orElse(null);
        if (loc == null || loc.getLatitude() == null || loc.getLongitude() == null) {
            throw new IllegalStateException("Provider location not available");
        }

        double startLat = loc.getLatitude();
        double startLng = loc.getLongitude();
        double avgSpeedKmh = 30.0;
        if (loc.getSpeed() != null && loc.getSpeed() > 0) {
            avgSpeedKmh = loc.getSpeed() * 3.6; // m/s -> km/h
        }

        double distance = LocationUtils.calculateDistance(startLat, startLng, destLat, destLng);
        return new Route(providerId, startLat, startLng, destLat, destLng, distance, avgSpeedKmh);
    }

    private List<Map<String, Object>> interpolate(
            double lat1, double lng1, double lat2, double lng2, int segments) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double lat = Math.round((lat1 + (lat2 - lat1) * t) * 1e6) / 1e6;
            double lng = Math.round((lng1 + (lng2 - lng1) * t) * 1e6) / 1e6;
            points.add(Map.of("lat", lat, "lng", lng));
        }
        return points;
    }

    private static class Route {
        final UUID providerId;
        final double startLat;
        final double startLng;
        final double endLat;
        final double endLng;
        final double distanceMeters;
        final double avgSpeedKmh;

        Route(UUID providerId, double startLat, double startLng,
              double endLat, double endLng, double distanceMeters, double avgSpeedKmh) {
            this.providerId = providerId;
            this.startLat = startLat;
            this.startLng = startLng;
            this.endLat = endLat;
            this.endLng = endLng;
            this.distanceMeters = distanceMeters;
            this.avgSpeedKmh = avgSpeedKmh;
        }
    }
}
