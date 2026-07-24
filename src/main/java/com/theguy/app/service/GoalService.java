package com.theguy.app.service;

import com.theguy.app.dto.GoalDTO;
import com.theguy.app.entity.ProviderAchievement;
import com.theguy.app.entity.ProviderGoal;
import com.theguy.app.repository.ProviderAchievementRepository;
import com.theguy.app.repository.ProviderGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final ProviderGoalRepository goalRepository;
    private final ProviderAchievementRepository achievementRepository;

    @Transactional
    public ProviderGoal getOrCreateWeeklyGoal(UUID providerId, double weeklyEarnings) {
        LocalDate today = LocalDate.now();
        int weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();

        return goalRepository.findByProviderAndWeek(providerId, year, weekNumber)
                .orElseGet(() -> {
                    ProviderGoal goal = new ProviderGoal();
                    goal.setProviderId(providerId);
                    goal.setWeeklyTarget(25000.0);
                    goal.setWeeklyProgress(weeklyEarnings);
                    goal.setWeekNumber(weekNumber);
                    goal.setYear(year);
                    return goalRepository.save(goal);
                });
    }

    @Transactional
    public ProviderGoal updateProgress(UUID providerId, double weeklyEarnings) {
        ProviderGoal goal = getOrCreateWeeklyGoal(providerId, weeklyEarnings);
        goal.setWeeklyProgress(weeklyEarnings);
        return goalRepository.save(goal);
    }

    @Transactional(readOnly = true)
    public GoalDTO getGoalDTO(UUID providerId, double weeklyEarnings) {
        ProviderGoal goal = getOrCreateWeeklyGoal(providerId, weeklyEarnings);
        int percentage = goal.getWeeklyTarget() > 0
                ? (int) Math.round((goal.getWeeklyProgress() / goal.getWeeklyTarget()) * 100)
                : 0;

        List<ProviderAchievement> achievements = achievementRepository.findByProviderId(providerId);
        List<GoalDTO.AchievementDTO> achievementDTOs = new ArrayList<>();

        achievementDTOs.add(buildAchievement("100_jobs", "100 Jobs Completed", "trophy",
                true, "2024-01-15", null, null));
        achievementDTOs.add(buildAchievement("50_stars", "50 Five-Star Reviews", "star",
                true, "2024-02-01", null, null));
        achievementDTOs.add(buildAchievement("fast_responder", "Fast Responder", "lightning",
                true, null, null, null));
        achievementDTOs.add(buildAchievement("30_day_streak", "30-Day Streak", "fire",
                false, null, 22, 30));
        achievementDTOs.add(buildAchievement("elite", "Elite Provider", "diamond",
                false, null, 87, 100));

        for (ProviderAchievement saved : achievements) {
            achievementDTOs.stream()
                    .filter(a -> a.getId().equals(saved.getAchievementId()))
                    .findFirst()
                    .ifPresent(a -> {
                        a.setUnlocked(saved.getUnlocked());
                        a.setDate(saved.getUnlockedAt() != null ? saved.getUnlockedAt().toLocalDate().toString() : null);
                        a.setProgress(saved.getProgress());
                        a.setTarget(saved.getTarget());
                    });
        }

        return GoalDTO.builder()
                .weeklyTarget(goal.getWeeklyTarget())
                .weeklyProgress(goal.getWeeklyProgress())
                .weeklyPercentage(percentage)
                .achievements(achievementDTOs)
                .build();
    }

    private GoalDTO.AchievementDTO buildAchievement(String id, String title, String icon,
                                                      boolean unlocked, String date,
                                                      Integer progress, Integer target) {
        return GoalDTO.AchievementDTO.builder()
                .id(id)
                .title(title)
                .icon(icon)
                .unlocked(unlocked)
                .date(date)
                .progress(progress)
                .target(target)
                .build();
    }
}
