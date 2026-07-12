package com.theguy.app.controller;

import com.theguy.app.dto.*;
import com.theguy.app.entity.AdminAction;
import com.theguy.app.entity.RiskScore;
import com.theguy.app.service.AdminActionService;
import com.theguy.app.service.RiskEngineService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final RiskEngineService riskEngineService;
    private final AdminActionService adminActionService;

    @GetMapping("/trust-safety/risk-scores")
    public ResponseEntity<ApiResponse<Page<RiskScoreDTO>>> getRiskScores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String riskLevel
    ) {
        Page<RiskScore> scores = riskEngineService.getRiskScores(riskLevel, PageRequest.of(page, size));
        Page<RiskScoreDTO> dto = scores.map(this::map);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/trust-safety/user/{userId}/action")
    public ResponseEntity<ApiResponse<Void>> takeUserAction(
            @PathVariable UUID userId,
            @RequestBody UserActionRequest request,
            HttpServletRequest servletRequest
    ) {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String adminPrincipal;
        if (principal instanceof String s) {
            adminPrincipal = s;
        } else {
            adminPrincipal = principal.toString();
        }

        adminActionService.executeUserAction(userId, request, servletRequest, adminPrincipal);

        // Recalculate risk after action
        riskEngineService.calculateRiskScore(userId, request.getUserType());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AdminAction>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) String actionType
    ) {
        Page<AdminAction> logs = adminActionService.getAuditLogs(adminId, actionType, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    private RiskScoreDTO map(RiskScore score) {
        RiskScoreDTO dto = new RiskScoreDTO();
        dto.setUserId(score.getUserId());
        dto.setUserType(score.getUserType());
        dto.setScore(score.getScore());
        dto.setRiskLevel(score.getRiskLevel());
        dto.setFactors(score.getFactors());
        dto.setRecommendations(score.getRecommendations());
        dto.setCalculatedAt(score.getCalculatedAt());
        dto.setExpiresAt(score.getExpiresAt());
        return dto;
    }
}

