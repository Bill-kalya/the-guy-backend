package com.theguy.app.controller;

import com.theguy.app.dto.*;
import com.theguy.app.dto.admin.*;
import com.theguy.app.entity.*;
import com.theguy.app.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final RiskEngineService riskEngineService;
    private final AdminActionService adminActionService;
    private final AdminFinanceService adminFinanceService;
    private final AdminUserService adminUserService;
    private final AdminProviderService adminProviderService;

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

    @GetMapping("/providers/summary")
    public ResponseEntity<ApiResponse<ProviderSummaryDTO>> getProviderSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminProviderService.getProviderSummary()));
    }

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<Page<ProviderListItemDTO>>> getProviders(
            @RequestParam(required = false) String verification,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminProviderService.getProviders(verification, status, minRating, search, page, size)));
    }

    @GetMapping("/providers/{providerId}")
    public ResponseEntity<ApiResponse<ProviderDetailDTO>> getProviderDetail(@PathVariable UUID providerId) {
        return ResponseEntity.ok(ApiResponse.success(adminProviderService.getProviderDetail(providerId)));
    }

    @GetMapping("/providers/{providerId}/financials")
    public ResponseEntity<ApiResponse<ProviderFinancialsDTO>> getProviderFinancials(@PathVariable UUID providerId) {
        return ResponseEntity.ok(ApiResponse.success(adminProviderService.getProviderFinancials(providerId)));
    }

    @GetMapping("/providers/{providerId}/performance")
    public ResponseEntity<ApiResponse<ProviderPerformanceDTO>> getProviderPerformance(@PathVariable UUID providerId) {
        return ResponseEntity.ok(ApiResponse.success(adminProviderService.getProviderPerformance(providerId)));
    }

    @GetMapping("/users/summary")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> getUserSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserSummary()));
    }

    @GetMapping("/users/risk-overview")
    public ResponseEntity<ApiResponse<RiskOverviewDTO>> getRiskOverview() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getRiskOverview()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserListItemDTO>>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUsers(role, search, page, size)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDetailDTO>> getUserDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(userId)));
    }

    @GetMapping("/finance/summary")
    public ResponseEntity<ApiResponse<FinanceSummaryDTO>> getFinanceSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminFinanceService.getFinanceSummary()));
    }

    @GetMapping("/finance/revenue-trend")
    public ResponseEntity<ApiResponse<List<RevenueTrendDTO>>> getRevenueTrend(
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminFinanceService.getRevenueTrend(days)));
    }

    @GetMapping("/finance/payouts/pending")
    public ResponseEntity<ApiResponse<List<PendingPayoutDTO>>> getPendingPayouts() {
        return ResponseEntity.ok(ApiResponse.success(adminFinanceService.getPendingPayouts()));
    }

    @GetMapping("/finance/ledger")
    public ResponseEntity<ApiResponse<Page<LedgerEntry>>> getLedgerEntries(
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) String entryType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminFinanceService.getLedgerEntries(accountCode, entryType, page, size)));
    }

    @GetMapping("/finance/tax-records")
    public ResponseEntity<ApiResponse<Page<TaxRecord>>> getTaxRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminFinanceService.getTaxRecords(page, size)));
    }

    @GetMapping("/finance/audit-trail")
    public ResponseEntity<ApiResponse<Page<FinancialAuditLog>>> getAuditTrail(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminFinanceService.getFinancialAuditTrail(page, size)));
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
