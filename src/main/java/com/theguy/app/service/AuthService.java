package com.theguy.app.service;

import com.theguy.app.entity.User;
import com.theguy.app.enums.AuthProvider;
import com.theguy.app.enums.OtpPurpose;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    @Value("${google.client-id:}")
    private String googleClientId;

    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=%s";

    @Transactional
    public void sendVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        otpService.generateAndSendOtp(email, OtpPurpose.VERIFY_EMAIL);
        log.info("Verification OTP sent to: {}", email);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Deliberately indistinguishable from success to prevent account enumeration
            log.info("Resend verification requested for unknown email (ignored)");
            return;
        }

        if (user.isVerified()) {
            throw new RuntimeException("Email already verified");
        }

        otpService.resendOtp(email, OtpPurpose.VERIFY_EMAIL);
        log.info("Verification OTP resent to: {}", email);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Deliberately indistinguishable from success to prevent account enumeration
            log.info("Password reset requested for unknown email (ignored)");
            return;
        }

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
    public User verifyEmailOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Email already verified");
        }

        otpService.verifyOtp(email, otp, OtpPurpose.VERIFY_EMAIL);

        user.setVerified(true);
        User savedUser = userRepository.save(user);

        log.info("Email verified for user: {}", email);
        return savedUser;
    }

    @Transactional
    public void verifyResetOtp(String email, String otp) {
        // Verify reset OTP; no DB changes beyond this
        otpService.verifyOtp(email, otp, OtpPurpose.RESET_PASSWORD);
    }

    /**
     * Verify a Google ID token, find-or-create the user, and return them.
     * - Validates audience (aud) matches our Google Client ID
     * - Validates email_verified is true
     * - Links avatar/name from Google to existing accounts
     * - Stores google_id and auth_provider for future account linking
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public User loginWithGoogle(String idToken) {
        Map<String, Object> tokenInfo = verifyGoogleToken(idToken);
        String email = (String) tokenInfo.get("email");
        String name = (String) tokenInfo.get("name");
        String picture = (String) tokenInfo.get("picture");
        String googleId = (String) tokenInfo.get("sub");
        Object emailVerified = tokenInfo.get("email_verified");

        if (email == null || email.isBlank()) {
            throw new RuntimeException("GOOGLE_AUTH_FAILED");
        }

        // Require email to be verified by Google
        if (emailVerified == null || Boolean.FALSE.equals(emailVerified)) {
            log.warn("Google account email not verified: {}", email);
            throw new RuntimeException("GOOGLE_AUTH_FAILED");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setFullName(name != null ? name : email.substring(0, email.indexOf('@')));
            newUser.setEmail(email);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRole(Role.CUSTOMER);
            newUser.setVerified(true);
            newUser.setAvatarUrl(picture);
            newUser.setGoogleId(googleId);
            newUser.setAuthProvider(AuthProvider.GOOGLE);
            newUser.setLastLoginAt(LocalDateTime.now());
            log.info("Created new Google user: {}", email);
            return userRepository.save(newUser);
        });

        // Link Google data to existing account
        boolean changed = false;
        if (!user.isVerified()) {
            user.setVerified(true);
            changed = true;
        }
        if (user.getGoogleId() == null && googleId != null) {
            user.setGoogleId(googleId);
            changed = true;
        }
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(AuthProvider.GOOGLE);
            changed = true;
        }
        if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && picture != null) {
            user.setAvatarUrl(picture);
            changed = true;
        }
        if ((user.getFullName() == null || user.getFullName().isBlank()) && name != null) {
            user.setFullName(name);
            changed = true;
        }
        user.setLastLoginAt(LocalDateTime.now());
        changed = true;

        if (changed) {
            userRepository.save(user);
        }

        return user;
    }

    /**
     * Call Google's tokeninfo endpoint to validate the ID token and extract claims.
     * Validates audience (aud) matches our configured Google Client ID.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyGoogleToken(String idToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = String.format(GOOGLE_TOKENINFO_URL, idToken);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("email")) {
                throw new RuntimeException("Invalid Google token");
            }

            // Verify audience matches our app's client ID
            if (googleClientId != null && !googleClientId.isBlank()) {
                String aud = (String) response.get("aud");
                if (aud == null || !aud.equals(googleClientId)) {
                    log.warn("Google token audience mismatch: expected={}, got={}", googleClientId, aud);
                    throw new RuntimeException("Invalid Google token audience");
                }
            }

            return response;
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Invalid Google token audience")) {
                throw e;
            }
            log.error("Google token verification failed: {}", e.getMessage());
            throw new RuntimeException("GOOGLE_AUTH_FAILED");
        }
    }
}