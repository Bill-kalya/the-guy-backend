package com.theguy.app.repository;

import com.theguy.app.entity.ProviderLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderLocationRepository extends JpaRepository<ProviderLocation, UUID> {
    
    Optional<ProviderLocation> findByProviderId(UUID providerId);
    
    @Query(value = "SELECT pl.* FROM provider_locations pl " +
           "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius) " +
           "ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) " +
           "LIMIT :limit", nativeQuery = true)
    List<ProviderLocation> findNearbyLocations(@Param("lat") double lat,
                                                @Param("lng") double lng,
                                                @Param("radius") double radius,
                                                @Param("limit") int limit);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM ProviderLocation pl WHERE pl.providerId = :providerId")
    void deleteByProviderId(@Param("providerId") UUID providerId);
}