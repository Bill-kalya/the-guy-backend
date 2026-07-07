package com.theguy.app.repository;

import com.theguy.app.entity.ProviderStatistics;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderStatisticsRepository extends JpaRepository<ProviderStatistics, UUID> {
}