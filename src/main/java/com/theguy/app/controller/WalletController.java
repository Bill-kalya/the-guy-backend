package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @GetMapping
    public ResponseEntity<?> getWallet(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        var wallet = walletService.getWallet(provider.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("pendingBalance", wallet.getPendingBalance());
        response.put("availableBalance", wallet.getAvailableBalance());
        response.put("currency", wallet.getCurrency());
        response.put("totalBalance", wallet.getPendingBalance() + wallet.getAvailableBalance());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        var transactions = walletService.getTransactions(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
}
