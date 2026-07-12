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

    @Query("SELECT a FROM AdminAction a " +
            "WHERE (:adminId IS NULL OR a.adminId = :adminId) " +
            "AND (:actionType IS NULL OR a.actionType = :actionType) " +
            "ORDER BY a.createdAt DESC")
    Page<AdminAction> findAuditLogs(
            @Param("adminId") UUID adminId,
            @Param("actionType") AdminAction.ActionType actionType,
            Pageable pageable
    );

    Optional<AdminAction> findFirstByAdminIdOrderByCreatedAtDesc(UUID adminId);
}

