package com.theguy.app.repository;

import com.theguy.app.entity.ProviderLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderLocationRepository extends JpaRepository<ProviderLocation, UUID> {

    Optional<ProviderLocation> findByProviderId(UUID providerId);

    /**
     * Find nearby providers using bounding box + haversine distance calculation
     * This is faster than using PostGIS and works on any database
     */
    @Query(value = """
        SELECT pl.*
        FROM provider_locations pl
        INNER JOIN providers p ON p.id = pl.provider_id
        WHERE
            p.is_online = true
            AND pl.latitude BETWEEN :minLat AND :maxLat
            AND pl.longitude BETWEEN :minLng AND :maxLng
            AND (
                6371000 * acos(
                    cos(radians(:lat))
                    * cos(radians(pl.latitude))
                    * cos(radians(pl.longitude) - radians(:lng))
                    + sin(radians(:lat))
                    * sin(radians(pl.latitude))
                )
            ) <= :radius
        ORDER BY
            (
                6371000 * acos(
                    cos(radians(:lat))
                    * cos(radians(pl.latitude))
                    * cos(radians(pl.longitude) - radians(:lng))
                    + sin(radians(:lat))
                    * sin(radians(pl.latitude))
                )
            ) ASC
        LIMIT 50
    """, nativeQuery = true)
    List<ProviderLocation> findNearbyProviders(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radius") double radius,
        @Param("minLat") double minLat,
        @Param("maxLat") double maxLat,
        @Param("minLng") double minLng,
        @Param("maxLng") double maxLng
    );

    /**
     * Find nearby providers filtered by service category
     */
    @Query(value = """
        SELECT DISTINCT pl.*
        FROM provider_locations pl
        INNER JOIN providers p ON p.id = pl.provider_id
        INNER JOIN services s ON s.provider_id = p.id AND s.is_active = true
        WHERE
            p.is_online = true
            AND s.category = :category
            AND pl.latitude BETWEEN :minLat AND :maxLat
            AND pl.longitude BETWEEN :minLng AND :maxLng
            AND (
                6371000 * acos(
                    cos(radians(:lat))
                    * cos(radians(pl.latitude))
                    * cos(radians(pl.longitude) - radians(:lng))
                    + sin(radians(:lat))
                    * sin(radians(pl.latitude))
                )
            ) <= :radius
        ORDER BY
            (
                6371000 * acos(
                    cos(radians(:lat))
                    * cos(radians(pl.latitude))
                    * cos(radians(pl.longitude) - radians(:lng))
                    + sin(radians(:lat))
                    * sin(radians(pl.latitude))
                )
            ) ASC
        LIMIT 50
    """, nativeQuery = true)
    List<ProviderLocation> findNearbyProvidersByCategory(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radius") double radius,
        @Param("minLat") double minLat,
        @Param("maxLat") double maxLat,
        @Param("minLng") double minLng,
        @Param("maxLng") double maxLng,
        @Param("category") String category
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM ProviderLocation pl WHERE pl.providerId = :providerId")
    void deleteByProviderId(@Param("providerId") UUID providerId);

    @Query("SELECT pl FROM ProviderLocation pl WHERE pl.providerId IN :providerIds")
    List<ProviderLocation> findByProviderIds(@Param("providerIds") List<UUID> providerIds);
}