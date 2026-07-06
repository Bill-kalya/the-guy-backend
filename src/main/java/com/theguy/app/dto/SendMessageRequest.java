package com.theguy.app.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {
    private UUID roomId;
    private UUID senderId;
    private String message;
}