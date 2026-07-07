package com.theguy.app.service;

import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.entity.Review;
import com.theguy.app.repository.ProviderStatisticsRepository;
import com.theguy.app.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderStatisticsService {
    
    private final ReviewRepository reviewRepository;
    private final ProviderStatisticsRepository providerStatisticsRepository;
    
    @Transactional
    public void recalculate(UUID providerId) {
        List<Review> reviews = reviewRepository.findByProviderId(providerId, 
            org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        
        if (reviews.isEmpty()) {
            // No reviews, set default values
            ProviderStatistics stats = providerStatisticsRepository.findById(providerId)
                .orElse(new ProviderStatistics());
            stats.setProviderId(providerId);
            stats.setSqs(0.0);
            stats.setProfessionalismScore(0.0);
            stats.setCommunicationScore(0.0);
            stats.setTimelinessScore(0.0);
            stats.setWorkQualityScore(0.0);
            stats.setValueScore(0.0);
            stats.setReliabilityScore(0.0);
            stats.setCourtesyScore(0.0);
            stats.setReviewCount(0);
            providerStatisticsRepository.save(stats);
            return;
        }
        
        // Calculate averages
        double sqs = reviews.stream()
                .mapToDouble(Review::getServiceQualityScore)
                .average()
                .orElse(0);
        
        double professionalism = reviews.stream()
                .mapToDouble(Review::getProfessionalism)
                .average()
                .orElse(0);
        
        double communication = reviews.stream()
                .mapToDouble(Review::getCommunication)
                .average()
                .orElse(0);
        
        double timeliness = reviews.stream()
                .mapToDouble(Review::getTimeliness)
                .average()
                .orElse(0);
        
        double workQuality = reviews.stream()
                .mapToDouble(Review::getWorkQuality)
                .average()
                .orElse(0);
        
        double valueForMoney = reviews.stream()
                .mapToDouble(Review::getValueForMoney)
                .average()
                .orElse(0);
        
        double reliability = reviews.stream()
                .mapToDouble(Review::getReliability)
                .average()
                .orElse(0);
        
        double courtesy = reviews.stream()
                .mapToDouble(Review::getCourtesy)
                .average()
                .orElse(0);
        
        // Save or update statistics
        ProviderStatistics stats = providerStatisticsRepository.findById(providerId)
            .orElse(new ProviderStatistics());
        stats.setProviderId(providerId);
        stats.setSqs(sqs);
        stats.setProfessionalismScore(professionalism);
        stats.setCommunicationScore(communication);
        stats.setTimelinessScore(timeliness);
        stats.setWorkQualityScore(workQuality);
        stats.setValueScore(valueForMoney);
        stats.setReliabilityScore(reliability);
        stats.setCourtesyScore(courtesy);
        stats.setReviewCount(reviews.size());
        
        providerStatisticsRepository.save(stats);
        log.info("Recalculated statistics for provider: {}", providerId);
    }
    
    @Transactional(readOnly = true)
    public java.util.Optional<ProviderStatistics> getStatistics(UUID providerId) {
        return providerStatisticsRepository.findById(providerId);
    }
}