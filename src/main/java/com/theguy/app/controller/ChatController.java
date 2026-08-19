package com.theguy.app.controller;

import com.theguy.app.dto.MessageDto;
import com.theguy.app.dto.SendMessageRequest;
import com.theguy.app.entity.ChatRoom;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ChatRoomRepository;
import com.theguy.app.repository.MessageRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> history(@RequestParam UUID roomId, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!room.getCustomerId().equals(user.getId()) && !room.getProviderId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: not a participant of this chat room"));
        }

        return ResponseEntity.ok(chatService.history(roomId));
    }

    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> send(@RequestBody SendMessageRequest request, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
            .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!room.getCustomerId().equals(user.getId()) && !room.getProviderId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: not a participant of this chat room"));
        }

        // Enforce sender identity — ignore client-supplied senderId
        request.setSenderId(user.getId());

        MessageDto message = chatService.send(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> read(@RequestParam UUID messageId, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        var message = messageRepository.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Message not found"));

        ChatRoom room = chatRoomRepository.findById(message.getRoomId())
            .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!room.getCustomerId().equals(user.getId()) && !room.getProviderId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: not a participant of this chat room"));
        }

        chatService.markRead(messageId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
