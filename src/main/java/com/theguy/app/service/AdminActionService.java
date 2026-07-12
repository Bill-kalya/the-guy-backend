package com.theguy.app.service;

import com.theguy.app.dto.UserActionRequest;
import com.theguy.app.entity.AdminAction;
import com.theguy.app.repository.AdminActionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminActionService {

    private final AdminActionRepository adminActionRepository;

    @Transactional
    public void executeUserAction(UUID userId, UserActionRequest request, HttpServletRequest servletRequest, String adminPrincipal) {
        AdminAction.ActionType actionType = AdminAction.ActionType.valueOf(request.getActionType());

        AdminAction action = new AdminAction();
        action.setAdminId(UUID.fromString(adminPrincipal));
        action.setActionType(actionType);
        action.setTargetId(userId.toString());
        action.setTargetType(request.getUserType());
        action.setDetails(request.getReason() != null ? request.getReason() : actionType.name());
        action.setIpAddress(servletRequest.getRemoteAddr());
        action.setUserAgent(servletRequest.getHeader("User-Agent"));
        action.setDeviceId(servletRequest.getHeader("X-Device-Id"));
        action.setMetadata(null);

        adminActionRepository.save(action);
    }

    @Transactional(readOnly = true)
    public Page<AdminAction> getAuditLogs(UUID adminId, String actionType, Pageable pageable) {
        AdminAction.ActionType parsed = (actionType == null || actionType.isBlank()) ? null : AdminAction.ActionType.valueOf(actionType);
        return adminActionRepository.findAuditLogs(adminId, parsed, pageable);
    }
}

