package com.theguy.app.repository;

import com.theguy.app.entity.ProviderAchievement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderAchievementRepository extends JpaRepository<ProviderAchievement, UUID> {

    @Query("SELECT a FROM ProviderAchievement a WHERE a.providerId = :providerId ORDER BY a.unlocked DESC, a.progress DESC")
    List<ProviderAchievement> findByProviderId(@Param("providerId") UUID providerId);
}
