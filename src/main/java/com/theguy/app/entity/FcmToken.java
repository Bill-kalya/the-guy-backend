package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fcm_tokens", indexes = {
    @Index(name = "idx_fcm_tokens_user", columnList = "user_id"),
    @Index(name = "idx_fcm_tokens_token", columnList = "token", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
public class FcmToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "device_platform")
    private String devicePlatform; // android, ios, web

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
