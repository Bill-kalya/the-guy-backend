package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.Dispute;
import com.theguy.app.entity.User;
import com.theguy.app.enums.DisputeStatus;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.DisputeService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> openDispute(@RequestBody OpenDisputeRequest request,
                                         Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        Dispute dispute = disputeService.openDispute(request.getJobId(), user.getId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Dispute opened", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @GetMapping("/status/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDisputeStatus(@PathVariable UUID jobId) {
        Dispute dispute = disputeService.getDisputeForJob(jobId);
        if (dispute == null) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "jobId", jobId,
                    "open", false
            )));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "jobId", jobId,
                "open", true,
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @PostMapping("/{disputeId}/investigate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> investigate(@PathVariable UUID disputeId) {
        Dispute dispute = disputeService.investigate(disputeId);
        return ResponseEntity.ok(ApiResponse.success("Dispute under investigation", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @PostMapping("/{disputeId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolve(@PathVariable UUID disputeId, @RequestBody ResolveDisputeRequest request) {
        Dispute dispute = disputeService.resolve(disputeId, request.getRefundAmount(),
                request.getProviderPenalty(), request.getNotes());
        return ResponseEntity.ok(ApiResponse.success("Dispute resolved", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @PostMapping("/{disputeId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reject(@PathVariable UUID disputeId, @RequestBody RejectDisputeRequest request) {
        Dispute dispute = disputeService.reject(disputeId, request.getNotes());
        return ResponseEntity.ok(ApiResponse.success("Dispute rejected", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getOpenDisputes() {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getOpenDisputes()));
    }

    @Data
    public static class OpenDisputeRequest {
        @NotNull private UUID jobId;
        @NotNull private String reason;
    }

    @Data
    public static class ResolveDisputeRequest {
        private Double refundAmount;
        private Double providerPenalty;
        private String notes;
    }

    @Data
    public static class RejectDisputeRequest {
        private String notes;
    }
}
