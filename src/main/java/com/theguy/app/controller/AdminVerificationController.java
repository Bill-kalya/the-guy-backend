package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.admin.VerificationDocumentAdminDTO;
import com.theguy.app.entity.User;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.AdminVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/verification")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVerificationController {

    private final AdminVerificationService adminVerificationService;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<VerificationDocumentAdminDTO>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminVerificationService.getPendingDocuments(page, size)));
    }

    @PostMapping("/{documentId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable UUID documentId) {
        adminVerificationService.approve(documentId, getAdminId());
        return ResponseEntity.ok(ApiResponse.success("Document approved", null));
    }

    @PostMapping("/{documentId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Rejected by admin") : "Rejected by admin";
        adminVerificationService.reject(documentId, getAdminId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Document rejected", null));
    }

    private UUID getAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated admin found");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            String username = userDetails.getUsername();
            try {
                return UUID.fromString(username);
            } catch (IllegalArgumentException e) {
                return userRepository.findByEmail(username)
                    .map(User::getId)
                    .orElseThrow(() -> new IllegalStateException("Admin user not found"));
            }
        }
        String principalStr = principal.toString();
        try {
            return UUID.fromString(principalStr);
        } catch (IllegalArgumentException e) {
            return userRepository.findByEmail(principalStr)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Admin user not found"));
        }
    }
}
