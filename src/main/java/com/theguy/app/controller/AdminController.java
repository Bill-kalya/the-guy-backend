package com.theguy.app.controller;

import com.theguy.app.dto.*;
import com.theguy.app.dto.admin.*;
import com.theguy.app.entity.*;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.*;
import com.theguy.app.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final ImpersonationService impersonationService;
    private final ProviderImportService providerImportService;
    private final ProviderClaimService providerClaimService;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

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

    // ── Impersonation ──────────────────────────────────────

    @PostMapping("/impersonate")
    public ResponseEntity<ApiResponse<ImpersonationTokenDTO>> impersonate(
            @RequestBody ImpersonationRequest request,
            HttpServletRequest servletRequest
    ) {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String adminPrincipal;
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            adminPrincipal = userDetails.getUsername();
        } else {
            adminPrincipal = principal.toString();
        }

        UUID adminId;
        try {
            adminId = UUID.fromString(adminPrincipal);
        } catch (IllegalArgumentException e) {
            // Principal is email — look up user
            adminId = userRepository.findByEmail(adminPrincipal)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Admin user not found for: " + adminPrincipal));
        }

        ImpersonationTokenDTO token = impersonationService.impersonate(request.getUserId(), adminId);

        // Audit log
        adminActionService.logImpersonation(adminId, request.getUserId(), servletRequest);

        return ResponseEntity.ok(ApiResponse.success(token));
    }

    // ── Audit Logs ────────────────────────────────────────
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AdminAction>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) String actionType
    ) {
        Page<AdminAction> logs = adminActionService.getAuditLogs(adminId, actionType, PageRequest.of(page, size, Sort.by("created_at").descending()));
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

    @PostMapping("/providers/import")
    public ResponseEntity<ApiResponse<ProviderImportResultDTO>> importProviders(
            @RequestParam("file") MultipartFile file) {
        ProviderImportResultDTO result = providerImportService.importProviders(file);
        return ResponseEntity.ok(ApiResponse.success(
                "Imported " + result.getImported() + " of " + result.getTotalRows() + " providers", result));
    }

    @PostMapping("/providers/{providerId}/claim-code")
    public ResponseEntity<ApiResponse<ProviderClaimCodeDTO>> regenerateClaimCode(@PathVariable UUID providerId) {
        String code = providerClaimService.generateClaimCode(providerId);
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        ProviderClaimCodeDTO dto = ProviderClaimCodeDTO.builder()
                .providerId(providerId)
                .claimCode(code)
                .expiresAt(provider.getClaimCodeExpiresAt())
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
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
