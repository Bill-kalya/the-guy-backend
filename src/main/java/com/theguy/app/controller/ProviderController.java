package com.theguy.app.controller;

import com.theguy.app.dto.*;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    
    private final ProviderService providerService;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final LocationService locationService;
    private final ProviderProfileCompletionService profileCompletionService;
    private final PerformanceService performanceService;
    private final GoalService goalService;
    private final ReputationService reputationService;
    private final InsightService insightService;
    private final WalletService walletService;
    private final DashboardService dashboardService;
    
    @PostMapping("/register")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<ProviderResponseDTO>> registerAsProvider(
            @Valid @RequestBody ProviderRegistrationDTO dto,
            Authentication authentication) {
        
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerService.registerProvider(user, dto);
        ProviderResponseDTO response = providerService.mapToResponseDTO(provider);
        
        return ResponseEntity.ok(ApiResponse.success("Provider registered successfully", response));
    }
    
    @GetMapping("/{providerId}")
    public ResponseEntity<ApiResponse<ProviderResponseDTO>> getProvider(@PathVariable UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        ProviderResponseDTO response = providerService.mapToResponseDTO(provider);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyProviderDTO>>> getNearbyProviders(
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lng,
            @RequestParam(defaultValue = "5000") @Min(100) @Max(50000) double radius,
            @RequestParam(required = false) String category) {
        
        try {
            List<NearbyProviderDTO> providers = locationService.findNearbyProviders(
                lat, lng, radius, category
            );
            return ResponseEntity.ok(ApiResponse.success(providers));
        } catch (Exception e) {
            log.error("Error finding nearby providers at ({}, {}) radius={}: {}", lat, lng, radius, e.getMessage(), e);
            throw e;
        }
    }
    
    @PatchMapping("/status")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Void>> updateOnlineStatus(@RequestParam boolean online, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        providerService.updateOnlineStatus(provider.getId(), online);
        
        return ResponseEntity.ok(ApiResponse.success("Status updated successfully", null));
    }
    
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<?> earnings(Authentication auth) {
        return ResponseEntity.ok(providerService.getEarnings(auth.getName()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<ProviderResponseDTO>> getMyProfile(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        ProviderResponseDTO response = providerService.mapToResponseDTO(provider);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/completion")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getProfileCompletion(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        java.util.Map<String, Object> completion = profileCompletionService.calculateCompletion(provider);
        return ResponseEntity.ok(ApiResponse.success(completion));
    }

    private Provider getCurrentProvider(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
        return providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
    }

    @GetMapping("/me/performance")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<PerformanceDTO>> getMyPerformance(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        PerformanceDTO performance = performanceService.getPerformanceDTO(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(performance));
    }

    @GetMapping("/me/goals")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<GoalDTO>> getMyGoals(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        GoalDTO goals = goalService.getGoalDTO(provider.getId(), 0.0);
        return ResponseEntity.ok(ApiResponse.success(goals));
    }

    @GetMapping("/me/reputation")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<ReputationDTO>> getMyReputation(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        ReputationDTO reputation = reputationService.getReputationDTO(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(reputation));
    }

    @GetMapping("/me/insights")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<InsightDTO>> getMyInsights(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        InsightDTO insights = insightService.getInsights(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(insights));
    }

    @GetMapping("/me/wallet")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getMyWallet(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        var wallet = walletService.getWallet(provider.getId());
        java.util.Map<String, Object> walletData = new java.util.HashMap<>();
        walletData.put("availableBalance", wallet.getAvailableBalance());
        walletData.put("pendingBalance", wallet.getPendingBalance());
        walletData.put("currency", wallet.getCurrency());
        return ResponseEntity.ok(ApiResponse.success(walletData));
    }

    @GetMapping("/me/dashboard")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> getMyDashboard(Authentication authentication) {
        Provider provider = getCurrentProvider(authentication);
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}