package com.theguy.app.service;

import com.theguy.app.dto.PriceEstimateDTO;
import com.theguy.app.enums.Urgency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PricingService {
    
    @Value("${pricing.base-multiplier:1.0}")
    private Double baseMultiplier;
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Base prices by category (KES)
    private static final java.util.Map<String, Double[]> BASE_PRICES = java.util.Map.of(
        "PLUMBING", new Double[]{500.0, 1500.0},
        "ELECTRICAL", new Double[]{600.0, 2000.0},
        "CLEANING", new Double[]{400.0, 1000.0},
        "MECHANIC", new Double[]{800.0, 3000.0},
        "DESIGN", new Double[]{2000.0, 10000.0},
        "TUTORING", new Double[]{500.0, 1500.0},
        "MOVING", new Double[]{1500.0, 5000.0},
        "REPAIR", new Double[]{400.0, 1200.0}
    );
    
    public PricingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PriceEstimateDTO estimate(String category, double lat, double lng, Urgency urgency) {
        log.debug("Calculating price estimate for category: {}, urgency: {}", category, urgency);
        
        // Get base price for category
        Double[] baseRange = BASE_PRICES.getOrDefault(category.toUpperCase(), new Double[]{500.0, 2000.0});
        Double baseMin = baseRange[0];
        Double baseMax = baseRange[1];
        
        // Apply urgency multiplier
        double urgencyMultiplier = getUrgencyMultiplier(urgency);
        
        // Apply time-of-day multiplier
        double timeMultiplier = getTimeOfDayMultiplier();
        
        // Apply demand multiplier (from cache)
        double demandMultiplier = getDemandMultiplier(category, lat, lng);
        
        // Apply location multiplier (Nairobi vs other areas)
        double locationMultiplier = getLocationMultiplier(lat, lng);
        
        // Calculate final range
        double finalMin = baseMin * urgencyMultiplier * timeMultiplier * demandMultiplier * locationMultiplier;
        double finalMax = baseMax * urgencyMultiplier * timeMultiplier * demandMultiplier * locationMultiplier;
        
        // Round to nearest 50 KES
        finalMin = Math.round(finalMin / 50) * 50;
        finalMax = Math.round(finalMax / 50) * 50;
        
        String breakdown = String.format(
            "Base: %.0f-%.0f | Urgency: %.1fx | Time: %.1fx | Demand: %.1fx | Location: %.1fx",
            baseMin, baseMax, urgencyMultiplier, timeMultiplier, demandMultiplier, locationMultiplier
        );
        
        return PriceEstimateDTO.builder()
            .minPrice(finalMin)
            .maxPrice(finalMax)
            .recommendedPrice((finalMin + finalMax) / 2)
            .currency("KES")
            .breakdown(breakdown)
            .isDynamic(true)
            .build();
    }
    
    private double getUrgencyMultiplier(Urgency urgency) {
        return urgency == Urgency.INSTANT ? 1.3 : 1.0;
    }
    
    private double getTimeOfDayMultiplier() {
        LocalTime now = LocalTime.now();
        
        // Late night (11 PM - 5 AM): 1.5x
        if (now.isAfter(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(5, 0))) {
            return 1.5;
        }
        // Early morning (5 AM - 7 AM): 1.2x
        else if (now.isAfter(LocalTime.of(5, 0)) && now.isBefore(LocalTime.of(7, 0))) {
            return 1.2;
        }
        // Rush hour (7 AM - 9 AM, 5 PM - 7 PM): 1.1x
        else if ((now.isAfter(LocalTime.of(7, 0)) && now.isBefore(LocalTime.of(9, 0))) ||
                 (now.isAfter(LocalTime.of(17, 0)) && now.isBefore(LocalTime.of(19, 0)))) {
            return 1.1;
        }
        // Weekend: 1.05x
        else if (LocalDateTime.now().getDayOfWeek().getValue() >= 6) {
            return 1.05;
        }
        return 1.0;
    }
    
    private double getDemandMultiplier(String category, double lat, double lng) {
        try {
            String cacheKey = String.format("demand:%s:%.2f:%.2f", category, lat, lng);
            Double demandMultiplier = (Double) redisTemplate.opsForValue().get(cacheKey);
            if (demandMultiplier != null) {
                return demandMultiplier;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for demand lookup: {}", e.getMessage());
        }
        return 1.0;
    }
    
    private double getLocationMultiplier(double lat, double lng) {
        // Nairobi CBD premium
        if (lat > -1.3 && lat < -1.25 && lng > 36.8 && lng < 36.85) {
            return 1.3;
        }
        // Nairobi suburbs
        else if (lat > -1.4 && lat < -1.2 && lng > 36.7 && lng < 36.95) {
            return 1.15;
        }
        // Mombasa
        else if (lat > -4.1 && lat < -4.0 && lng > 39.6 && lng < 39.7) {
            return 1.1;
        }
        // Kisumu
        else if (lat > -0.1 && lat < 0.1 && lng > 34.7 && lng < 34.8) {
            return 1.05;
        }
        // Other areas
        return 1.0;
    }
    
    public void updateDemandMultiplier(String category, double lat, double lng, double multiplier) {
        try {
            String cacheKey = String.format("demand:%s:%.2f:%.2f", category, lat, lng);
            redisTemplate.opsForValue().set(cacheKey, multiplier, 30, TimeUnit.MINUTES);
            log.info("Updated demand multiplier for {} at ({},{}): {}", category, lat, lng, multiplier);
        } catch (Exception e) {
            log.warn("Redis unavailable for demand update: {}", e.getMessage());
        }
    }
    
    public double calculateProviderPriceMultiplier(double rating, int jobsCompleted, double demandBoost) {
        // Base multiplier from rating
        double ratingMultiplier;
        if (rating >= 4.8) ratingMultiplier = 1.4;
        else if (rating >= 4.5) ratingMultiplier = 1.2;
        else if (rating >= 4.0) ratingMultiplier = 1.0;
        else if (rating >= 3.5) ratingMultiplier = 0.9;
        else ratingMultiplier = 0.8;
        
        // Experience bonus
        double experienceBonus = Math.min(0.2, jobsCompleted / 500.0);
        
        // Final multiplier
        return ratingMultiplier + experienceBonus + (demandBoost - 1.0);
    }
}