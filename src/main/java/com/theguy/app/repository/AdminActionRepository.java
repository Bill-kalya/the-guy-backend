package com.theguy.app.repository;

import com.theguy.app.entity.AdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {

    @Query(value = """
        SELECT * FROM admin_actions a
        WHERE (CAST(:adminId AS uuid) IS NULL OR a.admin_id = CAST(:adminId AS uuid))
          AND (CAST(:actionType AS varchar) IS NULL OR a.action_type = CAST(:actionType AS varchar))
        """, nativeQuery = true)
    Page<AdminAction> findAuditLogs(
            @Param("adminId") UUID adminId,
            @Param("actionType") String actionType,
            Pageable pageable
    );

    Optional<AdminAction> findFirstByAdminIdOrderByCreatedAtDesc(UUID adminId);

    long countByActionType(AdminAction.ActionType actionType);
}

