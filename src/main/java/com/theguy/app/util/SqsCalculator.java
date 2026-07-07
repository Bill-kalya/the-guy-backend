package com.theguy.app.util;

import com.theguy.app.dto.ReviewDTO;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class SqsCalculator {
    
    public double calculate(ReviewDTO request) {
        List<Integer> scores = new ArrayList<>();
        
        // Required scores
        scores.add(request.getOverallExperience());
        scores.add(request.getTimeliness());
        scores.add(request.getProfessionalism());
        scores.add(request.getCommunication());
        scores.add(request.getCourtesy());
        scores.add(request.getWorkQuality());
        scores.add(request.getAttentionToDetail());
        scores.add(request.getCleanliness());
        scores.add(request.getReliability());
        scores.add(request.getValueForMoney());
        scores.add(request.getRecommendation());
        
        // Optional score
        if (request.getProblemResolution() != null) {
            scores.add(request.getProblemResolution());
        }
        
        return scores.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }
}