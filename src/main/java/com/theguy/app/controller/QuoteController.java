package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.Quote;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.QuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @PostMapping
    public ResponseEntity<?> submitQuote(@Valid @RequestBody SubmitQuoteRequest request,
                                          Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        Quote quote = quoteService.submitQuote(
                request.getJobId(), provider.getId(),
                request.getAmount(), request.getDescription(),
                request.getEstimatedDurationMinutes());

        return ResponseEntity.ok(ApiResponse.success("Quote submitted", Map.of(
                "quoteId", quote.getId(),
                "amount", quote.getAmount(),
                "status", quote.getStatus().name()
        )));
    }

    @PostMapping("/{quoteId}/accept")
    public ResponseEntity<?> acceptQuote(@PathVariable UUID quoteId,
                                          Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Quote quote = quoteService.acceptQuote(quoteId, user.getId());

        return ResponseEntity.ok(ApiResponse.success("Quote accepted — price locked", Map.of(
                "quoteId", quote.getId(),
                "amount", quote.getAmount(),
                "status", quote.getStatus().name()
        )));
    }

    @PostMapping("/{quoteId}/reject")
    public ResponseEntity<?> rejectQuote(@PathVariable UUID quoteId,
                                          @RequestBody(required = false) RejectQuoteRequest request,
                                          Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Quote quote = quoteService.rejectQuote(quoteId, user.getId(),
                request != null ? request.getReason() : null);

        return ResponseEntity.ok(ApiResponse.success("Quote rejected", Map.of(
                "quoteId", quote.getId(),
                "status", quote.getStatus().name()
        )));
    }

    @PostMapping("/{quoteId}/counter")
    public ResponseEntity<?> counterOffer(@PathVariable UUID quoteId,
                                           @Valid @RequestBody CounterOfferRequest request,
                                           Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Quote quote = quoteService.counterOffer(quoteId, user.getId(), request.getCounterAmount());

        return ResponseEntity.ok(ApiResponse.success("Counter-offer submitted", Map.of(
                "quoteId", quote.getId(),
                "counterAmount", quote.getCounterAmount(),
                "status", quote.getStatus().name()
        )));
    }

    @PostMapping("/{quoteId}/accept-counter")
    public ResponseEntity<?> acceptCounterOffer(@PathVariable UUID quoteId,
                                                 Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        Quote quote = quoteService.acceptCounterOffer(quoteId, provider.getId());

        return ResponseEntity.ok(ApiResponse.success("Counter-offer accepted — price locked", Map.of(
                "quoteId", quote.getId(),
                "amount", quote.getAmount(),
                "status", quote.getStatus().name()
        )));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<?> getQuotesForJob(@PathVariable UUID jobId) {
        var quotes = quoteService.getQuotesForJob(jobId);
        return ResponseEntity.ok(ApiResponse.success(quotes));
    }

    @GetMapping("/provider")
    public ResponseEntity<?> getMyQuotes(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var provider = providerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));
        var quotes = quoteService.getProviderQuotes(provider.getId());
        return ResponseEntity.ok(ApiResponse.success(quotes));
    }

    @GetMapping("/customer")
    public ResponseEntity<?> getMyReceivedQuotes(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var quotes = quoteService.getCustomerQuotes(user.getId());
        return ResponseEntity.ok(ApiResponse.success(quotes));
    }

    @Data
    public static class SubmitQuoteRequest {
        @NotNull
        private UUID jobId;
        @NotNull @DecimalMin("50.00") @DecimalMax("1000000.00")
        private BigDecimal amount;
        @jakarta.validation.constraints.Size(max = 2000, message = "Description cannot exceed 2000 characters")
        private String description;
        @Min(15) @Max(1440)
        private Integer estimatedDurationMinutes = 60;
    }

    @Data
    public static class RejectQuoteRequest {
        @jakarta.validation.constraints.Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        private String reason;
    }

    @Data
    public static class CounterOfferRequest {
        @NotNull @DecimalMin("50.00")
        private BigDecimal counterAmount;
    }
}
