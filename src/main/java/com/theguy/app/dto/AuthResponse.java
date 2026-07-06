package com.theguy.app.dto;

import com.theguy.app.enums.Role;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UUID userId;
    private Role role;
    private String email;
    private String fullName;
    private boolean isVerified;
}