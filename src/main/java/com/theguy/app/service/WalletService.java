package com.theguy.app.service;

import com.theguy.app.entity.Wallet;
import com.theguy.app.entity.WalletTransaction;
import com.theguy.app.entity.Provider;
import com.theguy.app.enums.WalletEntryType;
import com.theguy.app.enums.WalletReferenceType;
import com.theguy.app.repository.WalletRepository;
import com.theguy.app.repository.WalletTransactionRepository;
import com.theguy.app.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ProviderRepository providerRepository;

    @Transactional
    public Wallet getOrCreateWallet(UUID providerId) {
        return walletRepository.findByProvider_Id(providerId)
                .orElseGet(() -> {
                    Provider provider = providerRepository.findById(providerId)
                            .orElseThrow(() -> new RuntimeException("Provider not found: " + providerId));
                    Wallet wallet = Wallet.builder()
                            .provider(provider)
                            .pendingBalance(0.0)
                            .availableBalance(0.0)
                            .currency("KES")
                            .build();
                    log.info("Created wallet for provider: {}", providerId);
                    return walletRepository.save(wallet);
                });
    }

    @Transactional
    public Wallet creditPending(UUID providerId, double amount, WalletReferenceType refType, UUID refId, String description) {
        Wallet wallet = getOrCreateWallet(providerId);
        wallet.setPendingBalance(wallet.getPendingBalance() + amount);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(WalletEntryType.CREDIT)
                .referenceType(refType)
                .referenceId(refId)
                .description(description)
                .build();
        walletTransactionRepository.save(tx);

        log.info("Wallet CREDIT pending: provider={}, amount={}, ref={}/{}", providerId, amount, refType, refId);
        return wallet;
    }

    @Transactional
    public Wallet releaseToAvailable(UUID providerId, double amount, String description) {
        Wallet wallet = getOrCreateWallet(providerId);
        if (wallet.getPendingBalance() < amount) {
            throw new IllegalStateException("Insufficient pending balance: " + wallet.getPendingBalance() + " < " + amount);
        }
        wallet.setPendingBalance(wallet.getPendingBalance() - amount);
        wallet.setAvailableBalance(wallet.getAvailableBalance() + amount);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(WalletEntryType.CREDIT)
                .referenceType(WalletReferenceType.ADJUSTMENT)
                .referenceId(wallet.getId())
                .description(description)
                .build();
        walletTransactionRepository.save(tx);

        log.info("Wallet RELEASED: provider={}, amount={}", providerId, amount);
        return wallet;
    }

    @Transactional
    public Wallet debitAvailable(UUID providerId, double amount, WalletReferenceType refType, UUID refId, String description) {
        Wallet wallet = getOrCreateWallet(providerId);
        if (wallet.getAvailableBalance() < amount) {
            throw new IllegalStateException("Insufficient available balance: " + wallet.getAvailableBalance() + " < " + amount);
        }
        wallet.setAvailableBalance(wallet.getAvailableBalance() - amount);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(WalletEntryType.DEBIT)
                .referenceType(refType)
                .referenceId(refId)
                .description(description)
                .build();
        walletTransactionRepository.save(tx);

        log.info("Wallet DEBIT available: provider={}, amount={}, ref={}/{}", providerId, amount, refType, refId);
        return wallet;
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID providerId) {
        return getOrCreateWallet(providerId);
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactions(UUID providerId) {
        Wallet wallet = getOrCreateWallet(providerId);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }
}
