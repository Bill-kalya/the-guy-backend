package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<?> getWallet(Authentication auth) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var wallet = walletService.getWallet(java.util.UUID.fromString(userId));

        Map<String, Object> response = new HashMap<>();
        response.put("pendingBalance", wallet.getPendingBalance());
        response.put("availableBalance", wallet.getAvailableBalance());
        response.put("currency", wallet.getCurrency());
        response.put("totalBalance", wallet.getPendingBalance() + wallet.getAvailableBalance());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(Authentication auth) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var transactions = walletService.getTransactions(java.util.UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
}
