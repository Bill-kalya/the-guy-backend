package com.theguy.app.repository;

import com.theguy.app.entity.Job;
import com.theguy.app.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.id = :id")
    Optional<Job> findByIdWithLock(@Param("id") UUID id);
    
    @Query("SELECT DISTINCT j FROM Job j " +
           "LEFT JOIN FETCH j.customer " +
           "LEFT JOIN FETCH j.provider " +
           "WHERE j.id = :id")
    Optional<Job> findByIdWithDetails(@Param("id") UUID id);
    
    List<Job> findByCustomerId(UUID customerId);
    
    List<Job> findByProviderId(UUID providerId);
    
    List<Job> findByProviderIdIn(List<UUID> providerIds);
    
    List<Job> findByStatus(JobStatus status);
    
    @Query("SELECT j FROM Job j WHERE j.status = :status AND j.createdAt < :timeout")
    List<Job> findStaleJobs(@Param("status") JobStatus status, @Param("timeout") LocalDateTime timeout);
    
    List<Job> findByStatusAndConfirmationDeadlineBefore(JobStatus status, LocalDateTime deadline);

    List<Job> findByCustomerIdAndStatus(UUID customerId, JobStatus status);

    long countByProviderIdAndStatus(UUID providerId, JobStatus status);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.provider.id = :providerId AND j.status = 'COMPLETED'")
    Long countCompletedByProvider(@Param("providerId") UUID providerId);
    
    @Query("SELECT AVG(j.finalPrice) FROM Job j WHERE j.serviceCategory = :category AND j.status = 'COMPLETED'")
    Double getAveragePriceByCategory(@Param("category") String category);
    
    @Query("SELECT COALESCE(SUM(j.finalPrice), 0) FROM Job j WHERE j.provider.id = :providerId AND j.status = 'COMPLETED'")
    Double getTotalEarningsByProvider(@Param("providerId") UUID providerId);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.provider.id = :providerId AND j.status = 'COMPLETED' AND j.completedAt >= :since")
    Long countCompletedByProviderSince(@Param("providerId") UUID providerId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(j.finalPrice), 0) FROM Job j WHERE j.provider.id = :providerId AND j.status = 'COMPLETED' AND j.completedAt >= :since")
    Double getTotalEarningsByProviderSince(@Param("providerId") UUID providerId, @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT j.provider.id FROM Job j WHERE j.provider.id IN :providerIds AND j.status IN :statuses")
    List<UUID> findProviderIdsWithActiveJobs(@Param("providerIds") List<UUID> providerIds,
                                             @Param("statuses") List<JobStatus> statuses);

    @Query(value = """
        SELECT j.* FROM jobs j
        WHERE j.status IN ('REQUESTED', 'MATCHING')
          AND j.provider_id IS NULL
          AND j.latitude BETWEEN :minLat AND :maxLat
          AND j.longitude BETWEEN :minLng AND :maxLng
        ORDER BY j.created_at DESC
        """, nativeQuery = true)
    List<Job> findOpenJobsNear(@Param("minLat") double minLat,
                               @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng,
                               @Param("maxLng") double maxLng);
}
