package com.theguy.app.controller;

import com.theguy.app.dto.MpesaRequest;
import com.theguy.app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mpesa/initiate")
    public ResponseEntity<?> initiate(@RequestBody MpesaRequest request) {
        return ResponseEntity.ok(paymentService.initiate(request));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam String checkoutId) {
        return ResponseEntity.ok(paymentService.status(checkoutId));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(Authentication auth) {
        return ResponseEntity.ok(paymentService.history(auth.getName()));
    }
}