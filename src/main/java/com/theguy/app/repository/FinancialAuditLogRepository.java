package com.theguy.app.repository;

import com.theguy.app.entity.FinancialAuditLog;
import com.theguy.app.enums.FinancialAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface FinancialAuditLogRepository extends JpaRepository<FinancialAuditLog, UUID> {
    Page<FinancialAuditLog> findByActionOrderByCreatedAtDesc(FinancialAction action, Pageable pageable);
    Page<FinancialAuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);
}
