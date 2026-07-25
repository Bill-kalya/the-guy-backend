package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.Payout;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.PayoutService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @PostMapping("/request")
    public ResponseEntity<?> requestPayout(@RequestBody PayoutRequest request, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        Payout payout = payoutService.requestPayout(provider.getId(), request.getAmount());
        return ResponseEntity.ok(ApiResponse.success("Payout requested", Map.of(
                "payoutId", payout.getId(),
                "amount", payout.getAmount(),
                "status", payout.getStatus().name()
        )));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getPayoutHistory(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        var payouts = payoutService.getProviderPayouts(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(payouts));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingPayouts() {
        var payouts = payoutService.getPendingPayouts();
        return ResponseEntity.ok(ApiResponse.success(payouts));
    }

    @PostMapping("/{payoutId}/approve")
    public ResponseEntity<?> approvePayout(@PathVariable UUID payoutId) {
        Payout payout = payoutService.approvePayout(payoutId);
        return ResponseEntity.ok(ApiResponse.success("Payout approved", Map.of(
                "payoutId", payout.getId(),
                "status", payout.getStatus().name()
        )));
    }

    @PostMapping("/{payoutId}/complete")
    public ResponseEntity<?> completePayout(@PathVariable UUID payoutId) {
        Payout payout = payoutService.completePayout(payoutId);
        return ResponseEntity.ok(ApiResponse.success("Payout completed", Map.of(
                "payoutId", payout.getId(),
                "status", payout.getStatus().name()
        )));
    }

    @PostMapping("/{payoutId}/fail")
    public ResponseEntity<?> failPayout(@PathVariable UUID payoutId) {
        Payout payout = payoutService.failPayout(payoutId);
        return ResponseEntity.ok(ApiResponse.success("Payout failed - funds returned", Map.of(
                "payoutId", payout.getId(),
                "status", payout.getStatus().name()
        )));
    }

    @Data
    public static class PayoutRequest {
        @NotNull
        private Double amount;
    }
}
