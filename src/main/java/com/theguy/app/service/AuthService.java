package com.theguy.app.service;

import com.theguy.app.entity.User;
import com.theguy.app.enums.OtpPurpose;
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
    private final OtpService otpService;

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Email already verified");
        }

        // Issue OTP via Redis + Resend
        otpService.resendOtp(email, OtpPurpose.VERIFY_EMAIL);
        log.info("Verification OTP resent to: {}", email);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Issue password reset OTP
        otpService.generateAndSendOtp(email, OtpPurpose.RESET_PASSWORD);
        log.info("Password reset OTP generated for: {}", email);
    }

    @Transactional
    public void resetPasswordWithOtp(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // This call assumes the OTP was already verified via verifyResetOtp
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    @Transactional
    public void verifyEmailOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Email already verified");
        }

        otpService.verifyOtp(email, otp, OtpPurpose.VERIFY_EMAIL);

        user.setVerified(true);
        userRepository.save(user);

        log.info("Email verified for user: {}", email);
    }

    @Transactional
    public void verifyResetOtp(String email, String otp) {
        // Verify reset OTP; no DB changes beyond this
        otpService.verifyOtp(email, otp, OtpPurpose.RESET_PASSWORD);
    }
}