package com.theguy.app.auth;

import com.theguy.app.dto.LoginRequest;
import com.theguy.app.dto.RegisterRequest;
import com.theguy.app.dto.AuthResponse;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Find user by phone number
            User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Authenticate
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    user.getId().toString(),
                    request.getPassword()
                )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate tokens
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole().name());
            claims.put("phone", user.getPhoneNumber());
            
            String accessToken = jwtUtil.generateToken(user.getId().toString(), claims);
            String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());
            
            return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .userId(user.getId())
                .role(user.getRole())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .build());
                
        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getPhoneNumber());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid phone number or password"));
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Authentication failed"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Check if user exists
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Phone number already registered"));
        }
        
        // Create new user
        User user = new User();
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setVerified(false);
        
        User savedUser = userRepository.save(user);
        
        // Generate token
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", savedUser.getRole().name());
        String accessToken = jwtUtil.generateToken(savedUser.getId().toString(), claims);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(86400000L)
            .userId(savedUser.getId())
            .role(savedUser.getRole())
            .phoneNumber(savedUser.getPhoneNumber())
            .fullName(savedUser.getFullName())
            .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String refreshToken) {
        if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
            String token = refreshToken.substring(7);
            try {
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
                log.error("Token refresh failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid refresh token"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Refresh token required"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // In production, add token to blacklist in Redis
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}