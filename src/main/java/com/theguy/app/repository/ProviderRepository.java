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

    @Query(value = """
        SELECT p.* FROM providers p 
        JOIN provider_locations pl ON p.id = pl.provider_id 
        WHERE ST_DWithin(
            pl.location::geography, 
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 
            :radius
        )
        AND p.is_online = true
        ORDER BY p.rating_avg DESC
        LIMIT 20
    """, nativeQuery = true)
    List<Provider> findNearbyProviders(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radius") double radius
    );
}
