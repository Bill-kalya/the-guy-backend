package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserDto {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private boolean isVerified;
    private LocalDateTime createdAt;
}