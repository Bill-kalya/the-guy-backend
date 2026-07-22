package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.Dispute;
import com.theguy.app.enums.DisputeStatus;
import com.theguy.app.service.DisputeService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<?> openDispute(@RequestBody OpenDisputeRequest request) {
        Dispute dispute = disputeService.openDispute(request.getJobId(), request.getUserId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Dispute opened", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getDisputeStatus(@PathVariable UUID jobId) {
        var disputes = disputeService.getOpenDisputes();
        return ResponseEntity.ok(ApiResponse.success(disputes));
    }

    @PostMapping("/{disputeId}/investigate")
    public ResponseEntity<?> investigate(@PathVariable UUID disputeId) {
        Dispute dispute = disputeService.investigate(disputeId);
        return ResponseEntity.ok(ApiResponse.success("Dispute under investigation", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable UUID disputeId, @RequestBody ResolveDisputeRequest request) {
        Dispute dispute = disputeService.resolve(disputeId, request.getRefundAmount(),
                request.getProviderPenalty(), request.getNotes());
        return ResponseEntity.ok(ApiResponse.success("Dispute resolved", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @PostMapping("/{disputeId}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID disputeId, @RequestBody RejectDisputeRequest request) {
        Dispute dispute = disputeService.reject(disputeId, request.getNotes());
        return ResponseEntity.ok(ApiResponse.success("Dispute rejected", Map.of(
                "disputeId", dispute.getId(),
                "status", dispute.getStatus().name()
        )));
    }

    @GetMapping("/open")
    public ResponseEntity<?> getOpenDisputes() {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getOpenDisputes()));
    }

    @Data
    public static class OpenDisputeRequest {
        @NotNull private UUID jobId;
        @NotNull private UUID userId;
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
