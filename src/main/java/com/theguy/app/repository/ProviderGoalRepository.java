package com.theguy.app.repository;

import com.theguy.app.entity.ProviderGoal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderGoalRepository extends JpaRepository<ProviderGoal, UUID> {

    @Query("SELECT g FROM ProviderGoal g WHERE g.providerId = :providerId AND g.year = :year AND g.weekNumber = :weekNumber")
    Optional<ProviderGoal> findByProviderAndWeek(@Param("providerId") UUID providerId,
                                                  @Param("year") int year,
                                                  @Param("weekNumber") int weekNumber);
}
