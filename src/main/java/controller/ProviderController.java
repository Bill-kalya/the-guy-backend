package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.ProviderRegistrationDTO;
import com.theguy.app.dto.ProviderResponseDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    
    private final ProviderService providerService;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    
    @PostMapping("/register")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ProviderResponseDTO>> registerAsProvider(
            @Valid @RequestBody ProviderRegistrationDTO dto) {
        
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        
        User user = userRepository.findById(UUID.fromString(userId))
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
    public ResponseEntity<ApiResponse<List<ProviderResponseDTO>>> getNearbyProviders(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double radius,
            @RequestParam(required = false) String category) {
        
        List<Provider> providers = providerRepository.findNearbyProviders(lat, lng, radius);
        
        if (category != null && !category.isEmpty()) {
            providers = providers.stream()
                .filter(p -> p.getServices().stream()
                    .anyMatch(s -> s.getCategory().equalsIgnoreCase(category)))
                .collect(Collectors.toList());
        }
        
        List<ProviderResponseDTO> response = providers.stream()
            .limit(20)
            .map(providerService::mapToResponseDTO)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PatchMapping("/status")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Void>> updateOnlineStatus(@RequestParam boolean online) {
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        providerService.updateOnlineStatus(provider.getId(), online);
        
        return ResponseEntity.ok(ApiResponse.success("Status updated successfully", null));
    }
    
    @PostMapping("/location")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @RequestParam double lat,
            @RequestParam double lng) {
        
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        providerService.updateLocation(provider.getId(), lat, lng);
        
        return ResponseEntity.ok(ApiResponse.success("Location updated successfully", null));
    }
    
    @GetMapping("/me")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<ProviderResponseDTO>> getMyProfile() {
        String userId = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Provider provider = providerRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        
        ProviderResponseDTO response = providerService.mapToResponseDTO(provider);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}