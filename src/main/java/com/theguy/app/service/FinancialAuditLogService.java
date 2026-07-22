package com.theguy.app.service;

import com.theguy.app.entity.FinancialAuditLog;
import com.theguy.app.enums.AuditActorType;
import com.theguy.app.enums.FinancialAction;
import com.theguy.app.repository.FinancialAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAuditLogService {

    private final FinancialAuditLogRepository auditLogRepository;

    @Transactional
    public FinancialAuditLog log(UUID actorId, AuditActorType actorType, FinancialAction action,
                                  String entityType, UUID entityId, String description) {
        FinancialAuditLog entry = FinancialAuditLog.builder()
                .actorId(actorId != null ? actorId : UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .actorType(actorType)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build();

        FinancialAuditLog saved = auditLogRepository.save(entry);
        log.info("Audit: {} {} {} by {} [{}]", action, entityType, entityId, actorType, description);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<FinancialAuditLog> getRecentLogs(int page, int size) {
        return auditLogRepository.findAll(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<FinancialAuditLog> getLogsByEntity(String entityType, UUID entityId, int page, int size) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtDesc(entityId, PageRequest.of(page, size));
    }
}
