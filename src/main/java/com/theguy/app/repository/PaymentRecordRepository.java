package com.theguy.app.repository;

import com.theguy.app.entity.PaymentRecord;
import com.theguy.app.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    Optional<PaymentRecord> findByTransactionCode(String transactionCode);
    List<PaymentRecord> findByCustomerId(UUID customerId);
    List<PaymentRecord> findByJobId(UUID jobId);
    long countByStatus(PaymentStatus status);
}
