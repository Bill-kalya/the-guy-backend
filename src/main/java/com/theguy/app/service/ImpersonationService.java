package com.theguy.app.service;

import com.theguy.app.auth.JwtUtil;
import com.theguy.app.dto.ImpersonationTokenDTO;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private static final long IMPERSONATION_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public ImpersonationTokenDTO impersonate(UUID targetUserId, UUID adminId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUserId));

        if (targetUser.getRole() == Role.ADMIN) {
            throw new RuntimeException("Cannot impersonate another admin");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", targetUser.getRole().name());
        claims.put("email", targetUser.getEmail());
        claims.put("impersonator_id", adminId.toString());
        claims.put("impersonator_role", Role.ADMIN.name());

        String token = jwtUtil.generateToken(targetUser.getEmail(), claims);

        log.info("Admin {} impersonating user {} ({})", adminId, targetUserId, targetUser.getRole().name());

        return ImpersonationTokenDTO.builder()
                .token(token)
                .expiresIn(IMPERSONATION_EXPIRY_MS)
                .targetUser(ImpersonationTokenDTO.TargetUser.builder()
                        .id(targetUser.getId())
                        .name(targetUser.getFullName())
                        .email(targetUser.getEmail())
                        .role(targetUser.getRole())
                        .build())
                .build();
    }
}
