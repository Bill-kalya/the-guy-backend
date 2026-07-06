package com.theguy.app.repository;

import com.theguy.app.entity.JobRequest;
import com.theguy.app.enums.JobRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobRequestRepository extends JpaRepository<JobRequest, UUID> {
    List<JobRequest> findByJobId(UUID jobId);
    List<JobRequest> findByProviderIdAndStatus(UUID providerId, JobRequestStatus status);
}
