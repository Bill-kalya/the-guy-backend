package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class GoalDTO {
    private Double weeklyTarget;
    private Double weeklyProgress;
    private Integer weeklyPercentage;
    private List<AchievementDTO> achievements;

    @Data
    @Builder
    public static class AchievementDTO {
        private String id;
        private String title;
        private String icon;
        private Boolean unlocked;
        private String date;
        private Integer progress;
        private Integer target;
    }
}
