package com.theguy.app.repository;

import com.theguy.app.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByUserId(UUID userId);

    List<Provider> findByIsOnlineTrue();

    List<Provider> findByCategoryId(String categoryId);

    List<Provider> findByIdInAndCategoryId(List<UUID> providerIds, String categoryId);

    @Query("SELECT DISTINCT p FROM Provider p LEFT JOIN FETCH p.portfolioImages pi WHERE p.id IN :providerIds")
    List<Provider> findAllByIdWithPortfolio(@Param("providerIds") List<UUID> providerIds);
}
