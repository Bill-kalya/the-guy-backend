package com.theguy.app.repository;

import com.theguy.app.entity.PortfolioImage;
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
public interface PortfolioImageRepository extends JpaRepository<PortfolioImage, UUID> {

    List<PortfolioImage> findByProviderIdAndIsActiveTrueOrderBySortOrderAsc(UUID providerId);

    Optional<PortfolioImage> findByPublicId(String publicId);

    @Query("SELECT pi FROM PortfolioImage pi WHERE pi.publicId = :publicId AND pi.provider.user.id = :userId")
    Optional<PortfolioImage> findByPublicIdAndUserId(@Param("publicId") String publicId, @Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE PortfolioImage pi SET pi.isActive = false WHERE pi.publicId = :publicId")
    void softDeleteByPublicId(@Param("publicId") String publicId);

    long countByProviderIdAndIsActiveTrue(UUID providerId);
}
