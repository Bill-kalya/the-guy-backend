package com.theguy.app.controller;

import com.theguy.app.dto.MpesaRequest;
import com.theguy.app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Deprecated
@RestController
@RequestMapping("/api/payments/legacy")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mpesa/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> initiate(@RequestBody MpesaRequest request, Authentication auth) {
        return ResponseEntity.ok(paymentService.initiate(request));
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> status(@RequestParam String checkoutId, Authentication auth) {
        return ResponseEntity.ok(paymentService.status(checkoutId));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> history(Authentication auth) {
        return ResponseEntity.ok(paymentService.history(auth.getName()));
    }
}
