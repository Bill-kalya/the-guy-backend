package com.theguy.app.service;

import com.theguy.app.entity.User;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Email already verified");
        }

        // Generate new verification token
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        userRepository.save(user);

        // TODO: Send verification email with link:
        // https://theguy.co.ke/verify?token=verificationToken
        log.info("Verification email resent to: {} with token: {}", email, verificationToken);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Generate password reset token
        String resetToken = UUID.randomUUID().toString();
        // Store reset token - could use a separate table or add field to User entity
        // For now, we reuse verificationToken field as a simple approach
        user.setVerificationToken(resetToken);
        userRepository.save(user);

        // TODO: Send password reset email with link:
        // https://theguy.co.ke/reset-password?token=resetToken
        log.info("Password reset requested for: {} with token: {}", email, resetToken);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setVerificationToken(null); // Clear the token
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }
}