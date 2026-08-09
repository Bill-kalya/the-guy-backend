package com.theguy.app.service;

import com.theguy.app.entity.Provider;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.utils.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderClaimService {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final int CODE_VALID_DAYS = 7;

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    /** Generates a fresh claim code for an unclaimed provider. */
    @Transactional
    public String generateClaimCode(java.util.UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

        if (provider.isAccountClaimed()) {
            throw new IllegalStateException("Provider account has already been claimed");
        }

        String code = generateCode();
        provider.setClaimCode(code);
        provider.setClaimCodeExpiresAt(LocalDateTime.now().plusDays(CODE_VALID_DAYS));
        providerRepository.save(provider);
        return code;
    }

    /** Claims the provider whose claim code matches, linking it to the authenticated user. */
    @Transactional
    public Provider claimProvider(User claimingUser, String rawPhone, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Claim code is required");
        }
        String code = rawCode.trim().toUpperCase();

        Provider provider = providerRepository.findByClaimCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid claim code"));

        if (provider.isAccountClaimed()) {
            throw new IllegalStateException("This account has already been claimed");
        }

        if (provider.getClaimCodeExpiresAt() != null
                && provider.getClaimCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Claim code has expired");
        }

        String phone = PhoneNormalizer.normalize(rawPhone);
        String linkedPhone = provider.getUser() != null
                ? PhoneNormalizer.normalize(provider.getUser().getPhoneNumber())
                : null;
        if (phone == null || linkedPhone == null || !phone.equals(linkedPhone)) {
            throw new IllegalArgumentException("Phone number does not match this provider");
        }

        if (providerRepository.findByUserId(claimingUser.getId()).isPresent()) {
            throw new IllegalStateException("Your account is already linked to a provider");
        }

        claimingUser.setRole(Role.PROVIDER);
        claimingUser.setPhoneNumber(phone);
        userRepository.save(claimingUser);

        provider.setUser(claimingUser);
        provider.setAccountClaimed(true);
        provider.setClaimCode(null);
        provider.setClaimCodeExpiresAt(null);
        providerRepository.save(provider);

        log.info("Provider {} claimed by user {}", provider.getId(), claimingUser.getId());
        return provider;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
