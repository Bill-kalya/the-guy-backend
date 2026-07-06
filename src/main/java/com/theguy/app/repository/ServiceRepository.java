package com.theguy.app.repository;

import com.theguy.app.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    
    List<Service> findByProviderId(UUID providerId);
    
    List<Service> findByProviderIdAndIsActiveTrue(UUID providerId);
    
    List<Service> findByCategory(String category);
    
    @Query("SELECT s FROM Service s WHERE s.provider.id = :providerId AND s.isActive = true")
    List<Service> findActiveServicesByProviderId(@Param("providerId") UUID providerId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Service s SET s.isActive = false WHERE s.provider.id = :providerId")
    void deactivateAllServicesByProviderId(@Param("providerId") UUID providerId);
    
    @Query("SELECT DISTINCT s.category FROM Service s")
    List<String> findAllCategories();
    
    @Query("SELECT COUNT(s) FROM Service s WHERE s.provider.id = :providerId")
    Long countByProviderId(@Param("providerId") UUID providerId);
}