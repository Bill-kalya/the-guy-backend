package com.theguy.app.repository;

import com.theguy.app.entity.TaxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, UUID> {
    Optional<TaxRecord> findByJobId(UUID jobId);
}
