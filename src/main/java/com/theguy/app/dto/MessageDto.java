package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageDto {
    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private String message;
    private boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}