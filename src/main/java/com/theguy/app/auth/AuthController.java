package com.theguy.app.auth;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.EmailRequest;
import com.theguy.app.dto.LoginRequest;
import com.theguy.app.dto.RegisterRequest;
import com.theguy.app.dto.AuthResponse;
import com.theguy.app.dto.ResetPasswordRequest;
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
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
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
                .body(ApiResponse.error("Invalid email or password"));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Account is locked"));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Account is disabled"));
        } catch (Exception e) {
            log.error("Login error for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Authentication failed"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Email already registered"));
        }
        
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.CUSTOMER);
        user.setVerified(false);
        
        // Generate verification token
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        
        User savedUser = userRepository.save(user);
        
        // TODO: Send verification email with link:
        // https://theguy.co.ke/verify?token=verificationToken
        
        log.info("User registered: {} with verification token: {}", savedUser.getEmail(), verificationToken);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", savedUser.getRole().name());
        String accessToken = jwtUtil.generateToken(savedUser.getEmail(), claims);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(86400000L)
            .userId(savedUser.getId())
            .role(savedUser.getRole())
            .email(savedUser.getEmail())
            .fullName(savedUser.getFullName())
            .isVerified(savedUser.isVerified())
            .build());
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        User user = userRepository.findByVerificationToken(token)
            .orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid or expired verification token"));
        }
        
        if (user.isVerified()) {
            return ResponseEntity.ok(ApiResponse.success("Email already verified", null));
        }
        
        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        
        log.info("Email verified for user: {}", user.getEmail());
        
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
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

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody EmailRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody EmailRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
