package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.Service;
import com.theguy.app.enums.PricingType;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ServiceRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
public class PricingController {

    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    @GetMapping("/pricing")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPricingConfig(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Provider provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        var services = serviceRepository.findByProviderIdAndIsActiveTrue(provider.getId());
        Service svc = services.isEmpty() ? null : services.get(0);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("serviceId", svc != null ? svc.getId() : null);
        config.put("pricingType", svc != null && svc.getPricingType() != null
                ? svc.getPricingType().name() : "CATALOG");
        config.put("basePrice", svc != null && svc.getBasePrice() != null
                ? svc.getBasePrice() : BigDecimal.valueOf(500));
        config.put("minPrice", svc != null ? svc.getMinPrice() : null);
        config.put("maxPrice", svc != null ? svc.getMaxPrice() : null);
        config.put("adjustmentPercent", svc != null && svc.getAdjustmentPercent() != null
                ? svc.getAdjustmentPercent() : 10);

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/pricing")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ApiResponse<Void>> updatePricingConfig(
            @Valid @RequestBody PricingConfigRequest request,
            Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Provider provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        var services = serviceRepository.findByProviderIdAndIsActiveTrue(provider.getId());
        Service svc = services.isEmpty() ? new Service() : services.get(0);

        if (svc.getId() == null) {
            svc.setProvider(provider);
            svc.setCategory("GENERAL");
            svc.setTitle("Provider Service");
        }

        svc.setPricingType(request.getPricingType());
        svc.setBasePrice(request.getBasePrice());
        svc.setMinPrice(request.getMinPrice());
        svc.setMaxPrice(request.getMaxPrice());
        svc.setAdjustmentPercent(request.getAdjustmentPercent());
        svc.setIsActive(true);

        serviceRepository.save(svc);

        return ResponseEntity.ok(ApiResponse.success("Pricing configuration updated", null));
    }

    @Data
    public static class PricingConfigRequest {
        @NotNull
        private PricingType pricingType;

        @NotNull @DecimalMin("50.00") @DecimalMax("10000000.00")
        private BigDecimal basePrice;

        private BigDecimal minPrice;

        private BigDecimal maxPrice;

        @Min(0) @Max(25)
        private Integer adjustmentPercent = 10;
    }
}
