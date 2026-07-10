package com.theguy.app.utils;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LocationUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

    /**
     * Calculate distance between two points in meters using Haversine formula
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = lat1 * DEGREES_TO_RADIANS;
        double lat2Rad = lat2 * DEGREES_TO_RADIANS;
        double deltaLat = (lat2 - lat1) * DEGREES_TO_RADIANS;
        double deltaLng = (lng2 - lng1) * DEGREES_TO_RADIANS;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculate ETA in minutes based on distance and average speed
     */
    public static int calculateETA(double distanceMeters, double avgSpeedKmh) {
        if (avgSpeedKmh <= 0) {
            avgSpeedKmh = 30.0; // Default average speed for city driving
        }
        double timeHours = (distanceMeters / 1000.0) / avgSpeedKmh;
        return (int) Math.ceil(timeHours * 60); // Return minutes
    }

    /**
     * Get bounding box for a given center point and radius (in meters)
     * Uses approximate 1 degree = 111km at equator
     */
    public static BoundingBox getBoundingBox(double lat, double lng, double radiusMeters) {
        // 1 degree latitude ≈ 111,320 meters
        // 1 degree longitude ≈ 111,320 * cos(latitude) meters
        double latDelta = radiusMeters / 111320.0;
        double lngDelta = radiusMeters / (111320.0 * Math.cos(lat * DEGREES_TO_RADIANS));

        return new BoundingBox(
            lat - latDelta,
            lat + latDelta,
            lng - lngDelta,
            lng + lngDelta
        );
    }

    public static class BoundingBox {
        public final double minLat;
        public final double maxLat;
        public final double minLng;
        public final double maxLng;

        public BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLng = minLng;
            this.maxLng = maxLng;
        }
    }

    /**
     * Calculate quality score based on multiple factors
     */
    public static double calculateQualityScore(double rating, int jobsCompleted, double responseRate) {
        // Rating (0-5) -> (0-50 points)
        double ratingScore = (rating / 5.0) * 50;
        
        // Jobs completed (0-1000+) -> (0-30 points)
        double jobsScore = Math.min(30, (jobsCompleted / 1000.0) * 30);
        
        // Response rate (0-1) -> (0-20 points)
        double responseScore = responseRate * 20;
        
        return Math.min(100, ratingScore + jobsScore + responseScore);
    }
}