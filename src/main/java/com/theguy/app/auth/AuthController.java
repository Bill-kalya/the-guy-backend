package com.theguy.app.auth;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.EmailRequest;
import com.theguy.app.dto.LoginRequest;
import com.theguy.app.dto.RegisterRequest;
import com.theguy.app.dto.AuthResponse;
import com.theguy.app.dto.ResetPasswordRequest;
import com.theguy.app.dto.StructuredErrorResponse;
import com.theguy.app.enums.ErrorCode;
import com.theguy.app.service.AuthService;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
            
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (!user.isVerified()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("EMAIL_NOT_VERIFIED"));
            }
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole().name());
            claims.put("email", user.getEmail());
            
            String accessToken = jwtUtil.generateToken(request.getEmail(), claims);
            String refreshToken = jwtUtil.generateRefreshToken(request.getEmail());
            
            return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .userId(user.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isVerified(user.isVerified())
                .build());
                
        } catch (BadCredentialsException e) {
            log.warn("Invalid login attempt for email: {}", request.getEmail());
            rateLimitInterceptor.recordFailedAttempt(getClientIp(httpRequest));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(StructuredErrorResponse.builder()
                    .success(false)
                    .errorCode(ErrorCode.INVALID_CREDENTIALS.getCode())
                    .message(ErrorCode.INVALID_CREDENTIALS.getMessage())
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build());
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(StructuredErrorResponse.builder()
                    .success(false)
                    .errorCode(ErrorCode.ACCOUNT_LOCKED.getCode())
                    .message(ErrorCode.ACCOUNT_LOCKED.getMessage())
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build());
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(StructuredErrorResponse.builder()
                    .success(false)
                    .errorCode(ErrorCode.ACCOUNT_SUSPENDED.getCode())
                    .message(ErrorCode.ACCOUNT_SUSPENDED.getMessage())
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build());
        } catch (Exception e) {
            log.error("Login error for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StructuredErrorResponse.builder()
                    .success(false)
                    .errorCode(ErrorCode.SERVER_ERROR.getCode())
                    .message(ErrorCode.SERVER_ERROR.getMessage())
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(StructuredErrorResponse.builder()
                    .success(false)
                    .errorCode(ErrorCode.EMAIL_EXISTS.getCode())
                    .message(ErrorCode.EMAIL_EXISTS.getMessage())
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build());
        }
        
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.CUSTOMER);
        user.setVerified(false);
        
        User savedUser = userRepository.save(user);

        boolean otpSent = true;
        try {
            authService.sendVerificationOtp(savedUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification OTP for {}: {}", savedUser.getEmail(), e.getMessage());
            otpSent = false;
        }

        log.info("User registered: {} (otpSent={})", savedUser.getEmail(), otpSent);

        Map<String, Object> body = new HashMap<>();
        body.put("email", savedUser.getEmail());
        body.put("otpSent", otpSent);
        body.put("message", otpSent
            ? "Registration successful. Please verify your email."
            : "Registration successful. We couldn't send a verification code — please tap Resend.");

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            body.get("message").toString(), body));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody com.theguy.app.dto.VerifyOtpRequest request) {
        try {
            User user = authService.verifyEmailOtp(request.getEmail(), request.getOtp());

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole().name());
            claims.put("email", user.getEmail());

            String accessToken = jwtUtil.generateToken(user.getEmail(), claims);
            String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

            return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .userId(user.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isVerified(true)
                .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody EmailRequest request) {
        try {
            authService.resendVerification(request.getEmail());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String refreshTokenHeader) {
        if (refreshTokenHeader != null && refreshTokenHeader.startsWith("Bearer ")) {
            String token = refreshTokenHeader.substring(7);
            try {
                String jti = jwtUtil.getTokenId(token);
                String blacklistKey = "token_blacklist:" + jti;
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("Token has been revoked"));
                    }
                } catch (Exception e) {
                    log.warn("Redis unavailable for token blacklist check: {}", e.getMessage());
                }

                String userId = jwtUtil.extractUserId(token);
                if (userId != null && jwtUtil.validateToken(token, userId)) {
                    Map<String, Object> claims = jwtUtil.getClaimsFromToken(token);
                    String newAccessToken = jwtUtil.generateToken(userId, claims);
                    return ResponseEntity.ok(Map.of(
                        "accessToken", newAccessToken,
                        "tokenType", "Bearer"
                    ));
                }
            } catch (Exception e) {
                log.error("Token refresh failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid refresh token"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Refresh token required"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtUtil.getTokenId(token);
                String blacklistKey = "token_blacklist:" + jti;
                try {
                    redisTemplate.opsForValue().set(blacklistKey, "revoked", 7, TimeUnit.DAYS);
                    log.info("Token revoked: {}", jti);
                } catch (Exception e) {
                    log.warn("Redis unavailable for token blacklist: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to parse token for logout: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody EmailRequest request) {
        try {
            authService.forgotPassword(request.getEmail());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<?> verifyResetOtp(@Valid @RequestBody com.theguy.app.dto.VerifyOtpRequest request) {
        try {
            authService.verifyResetOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody com.theguy.app.dto.ResetPasswordRequest request) {
        try {
            authService.resetPasswordWithOtp(request.getEmail(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
