package com.theguy.app.service;

import com.theguy.app.entity.Payout;
import com.theguy.app.entity.Provider;
import com.theguy.app.enums.PayoutStatus;
import com.theguy.app.enums.WalletReferenceType;
import com.theguy.app.repository.PayoutRepository;
import com.theguy.app.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final ProviderRepository providerRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final FinancialAuditLogService auditLogService;

    @Transactional
    public Payout requestPayout(UUID providerId, double amount) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        // Create payout entity first to get the ID for reservation
        Payout payout = Payout.builder()
                .provider(provider)
                .amount(amount)
                .method(com.theguy.app.enums.PaymentMethod.MPESA)
                .status(PayoutStatus.PENDING)
                .build();
        Payout saved = payoutRepository.save(payout);

        // Reserve funds: availableBalance -> reservedBalance (prevents duplicate withdrawals)
        walletService.reserveForPayout(providerId, amount, saved.getId(), "Payout request reserved");

        ledgerService.recordDoubleEntry(
                com.theguy.app.enums.AccountCode.PAYOUT_OUTSTANDING,
                com.theguy.app.enums.AccountCode.PROVIDER_EARNINGS,
                amount, "KES", "PAYOUT", saved.getId(), "Payout requested by provider");

        log.info("Payout requested: provider={}, amount={}, payoutId={}", providerId, amount, saved.getId());

        auditLogService.log(providerId, com.theguy.app.enums.AuditActorType.PROVIDER,
                com.theguy.app.enums.FinancialAction.PAYOUT_REQUESTED,
                "Payout", saved.getId(), String.format("Provider requested payout of KES %.2f", amount));

        return saved;
    }

    @Transactional
    public Payout approvePayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setTransactionReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payoutRepository.save(payout);

        auditLogService.log(null, com.theguy.app.enums.AuditActorType.ADMIN,
                com.theguy.app.enums.FinancialAction.PAYOUT_APPROVED,
                "Payout", payoutId, "Payout approved by admin");

        log.info("Payout approved: payoutId={}", payoutId);
        return payout;
    }

    @Transactional
    public Payout completePayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setProcessedAt(LocalDateTime.now());
        payoutRepository.save(payout);

        // Confirm reservation: reservedBalance -> deducted (funds sent to provider)
        walletService.confirmReservation(payout.getProvider().getId(), payout.getAmount(),
                payoutId, "Payout completed via M-Pesa");

        auditLogService.log(null, com.theguy.app.enums.AuditActorType.SYSTEM,
                com.theguy.app.enums.FinancialAction.PAYOUT_COMPLETED,
                "Payout", payoutId, "Payout completed via M-Pesa");

        log.info("Payout completed: payoutId={}, txn={}", payoutId, payout.getTransactionReference());
        return payout;
    }

    @Transactional
    public Payout failPayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));
        payout.setStatus(PayoutStatus.FAILED);
        payoutRepository.save(payout);

        // Release reservation: reservedBalance -> availableBalance (funds return to available)
        walletService.releaseReservation(payout.getProvider().getId(), payout.getAmount(),
                payoutId, "Payout failed - reservation released back to available");

        auditLogService.log(null, com.theguy.app.enums.AuditActorType.SYSTEM,
                com.theguy.app.enums.FinancialAction.PAYOUT_FAILED,
                "Payout", payoutId, "Payout failed - funds returned to available balance");

        log.warn("Payout failed: payoutId={}", payoutId);
        return payout;
    }

    @Transactional(readOnly = true)
    public List<Payout> getProviderPayouts(UUID providerId) {
        return payoutRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    @Transactional(readOnly = true)
    public List<Payout> getPendingPayouts() {
        return payoutRepository.findByStatus(PayoutStatus.PENDING);
    }
}
