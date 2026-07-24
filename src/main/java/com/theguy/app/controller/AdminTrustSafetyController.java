package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.service.TrustSafetyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/trust-safety")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTrustSafetyController {

    private final TrustSafetyService trustSafetyService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(trustSafetyService.getSummary()));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAlerts(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(trustSafetyService.getCriticalAlerts(limit)));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHeatmap() {
        return ResponseEntity.ok(ApiResponse.success(trustSafetyService.getHeatmap()));
    }

    @GetMapping("/moderation-queue")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> getModerationQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                trustSafetyService.getModerationQueue(status, page, size)));
    }

    @PostMapping("/providers/{providerId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendProvider(
            @PathVariable UUID providerId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String reason = body != null ? body.getOrDefault("reason", "Suspended by admin") : "Suspended by admin";
        trustSafetyService.suspendProvider(providerId, reason, getAdminId(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Provider suspended", null));
    }

    @PostMapping("/providers/{providerId}/ban")
    public ResponseEntity<ApiResponse<Void>> banProvider(
            @PathVariable UUID providerId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String reason = body != null ? body.getOrDefault("reason", "Banned by admin") : "Banned by admin";
        trustSafetyService.banProvider(providerId, reason, getAdminId(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Provider banned", null));
    }

    @PostMapping("/providers/{providerId}/reinstate")
    public ResponseEntity<ApiResponse<Void>> reinstateProvider(
            @PathVariable UUID providerId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String reason = body != null ? body.getOrDefault("reason", "Reinstated by admin") : "Reinstated by admin";
        trustSafetyService.reinstateProvider(providerId, reason, getAdminId(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Provider reinstated", null));
    }

    private UUID getAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated admin found");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(principal.toString());
    }
}
