package com.theguy.app.dto;

import com.theguy.app.enums.Role;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ImpersonationTokenDTO {
    private String token;
    private Long expiresIn;
    private TargetUser targetUser;

    @Data
    @Builder
    public static class TargetUser {
        private UUID id;
        private String name;
        private String email;
        private Role role;
    }
}
