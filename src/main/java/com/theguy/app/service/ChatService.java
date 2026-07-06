package com.theguy.app.service;

import com.theguy.app.dto.MessageDto;
import com.theguy.app.dto.SendMessageRequest;
import com.theguy.app.entity.Message;
import com.theguy.app.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<MessageDto> history(UUID roomId) {
        return messageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageDto send(SendMessageRequest request) {
        Message message = new Message();
        message.setRoomId(request.getRoomId());
        message.setSenderId(request.getSenderId());
        message.setMessage(request.getMessage());
        message.setRead(false);

        Message savedMessage = messageRepository.save(message);
        log.info("Message sent in room {} by sender {}", request.getRoomId(), request.getSenderId());

        return mapToDto(savedMessage);
    }

    @Transactional
    public void markRead(UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        message.setRead(true);
        message.setReadAt(LocalDateTime.now());
        messageRepository.save(message);

        log.info("Message {} marked as read", messageId);
    }

    private MessageDto mapToDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .message(message.getMessage())
                .isRead(message.isRead())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}