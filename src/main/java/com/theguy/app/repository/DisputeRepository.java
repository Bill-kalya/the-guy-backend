package com.theguy.app.repository;

import com.theguy.app.entity.Dispute;
import com.theguy.app.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByJobId(UUID jobId);
    List<Dispute> findByStatus(DisputeStatus status);
    long countByStatus(DisputeStatus status);
}
