package com.theguy.app.controller;

import com.theguy.app.dto.MessageDto;
import com.theguy.app.dto.SendMessageRequest;
import com.theguy.app.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/history")
    public List<MessageDto> history(@RequestParam UUID roomId) {
        return chatService.history(roomId);
    }

    @PostMapping("/send")
    public MessageDto send(@RequestBody SendMessageRequest request) {
        return chatService.send(request);
    }

    @PostMapping("/read")
    public void read(@RequestParam UUID messageId) {
        chatService.markRead(messageId);
    }
}