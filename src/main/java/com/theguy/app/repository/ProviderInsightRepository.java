package com.theguy.app.repository;

import com.theguy.app.entity.ProviderInsight;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProviderInsightRepository extends JpaRepository<ProviderInsight, UUID> {

    @Query("SELECT i FROM ProviderInsight i WHERE i.providerId = :providerId ORDER BY i.generatedAt DESC")
    List<ProviderInsight> findByProviderId(@Param("providerId") UUID providerId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProviderInsight i WHERE i.providerId = :providerId")
    void deleteByProviderId(@Param("providerId") UUID providerId);
}
