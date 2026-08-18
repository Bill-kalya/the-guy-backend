package com.theguy.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {
    @NotNull(message = "Room ID is required")
    private UUID roomId;
    @NotNull(message = "Sender ID is required")
    private UUID senderId;
    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message cannot exceed 5000 characters")
    private String message;
}