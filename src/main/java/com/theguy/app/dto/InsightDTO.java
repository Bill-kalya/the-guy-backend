package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class InsightDTO {
    private List<InsightItem> strengths;
    private List<InsightItem> improvements;
    private String trend;

    @Data
    @Builder
    public static class InsightItem {
        private String category;
        private String message;
        private Double score;
    }
}
