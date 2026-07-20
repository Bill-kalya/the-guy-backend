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

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE s.category = :category AND s.isActive = true")
    List<Provider> findByServiceCategory(@Param("category") String category);

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE p.id IN :providerIds AND s.category = :category AND s.isActive = true")
    List<Provider> findByIdInAndServiceCategory(@Param("providerIds") List<UUID> providerIds, @Param("category") String category);

    @Query("SELECT DISTINCT p FROM Provider p LEFT JOIN FETCH p.services WHERE p.id IN :providerIds")
    List<Provider> findAllByIdWithServices(@Param("providerIds") List<UUID> providerIds);
}
