package com.theguy.app.controller;

import com.theguy.app.entity.Notification;
import com.theguy.app.entity.User;
import com.theguy.app.repository.NotificationRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmTokenService fcmTokenService;

    private User requireUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        User user = requireUser(auth);
        Page<Notification> notifications = notificationRepository
            .findAllByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, size));
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());

        return ResponseEntity.ok(Map.of(
            "notifications", notifications.getContent(),
            "totalPages", notifications.getTotalPages(),
            "totalElements", notifications.getTotalElements(),
            "unreadCount", unreadCount
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication auth) {
        User user = requireUser(auth);
        long count = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PostMapping("/mark-read")
    public ResponseEntity<?> markAllRead(Authentication auth) {
        User user = requireUser(auth);
        notificationRepository.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<?> registerFcmToken(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        User user = requireUser(auth);
        String token = body.get("token");
        String platform = body.getOrDefault("platform", "android");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token required"));
        }

        fcmTokenService.registerToken(user.getId(), token, platform);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/fcm-token")
    public ResponseEntity<?> removeFcmToken(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String token = body.get("token");
        if (token != null) {
            fcmTokenService.removeToken(token);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
