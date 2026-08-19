package com.theguy.app.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.theguy.app.entity.FcmToken;
import com.theguy.app.repository.FcmTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    @Value("${firebase.config-path:}")
    private Resource firebaseConfigPath;

    @PostConstruct
    public void init() {
        try {
            if (firebaseConfigPath != null && firebaseConfigPath.exists()) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(firebaseConfigPath.getInputStream()))
                    .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully");
            } else {
                log.warn("Firebase config not found; push notifications disabled");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void sendToUser(String userId, String title, String body,
                            String type, Map<String, String> data) {
        try {
            List<FcmToken> tokens = fcmTokenRepository.findAllByUserId(
                java.util.UUID.fromString(userId));
            if (tokens.isEmpty()) {
                log.debug("No FCM tokens for user {}", userId);
                return;
            }

            MulticastMessage message = MulticastMessage.builder()
                .putAllData(data != null ? data : Map.of("type", type))
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .addAllTokens(tokens.stream().map(FcmToken::getToken).toList())
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build())
                .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            if (response.getFailureCount() > 0) {
                List<SendResponse> failed = response.getResponses().stream()
                    .filter(r -> !r.isSuccessful())
                    .toList();
                log.warn("FCM: {} of {} messages failed for user {}",
                    failed.size(), response.getSuccessCount() + failed.size(), userId);

                failed.forEach(r -> {
                    String token = tokens.get(response.getResponses().indexOf(r)).getToken();
                    if (r.getException() instanceof FirebaseMessagingException) {
                        FirebaseMessagingException fme = (FirebaseMessagingException) r.getException();
                        if (fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                            fme.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                            fcmTokenRepository.deleteByToken(token);
                            log.info("Removed stale FCM token: {}", token.substring(0, Math.min(20, token.length())));
                        }
                    }
                });
            } else {
                log.debug("FCM: {} messages sent to user {}", response.getSuccessCount(), userId);
            }
        } catch (Exception e) {
            log.error("FCM send failed for user {}: {}", userId, e.getMessage());
        }
    }

    public boolean isConfigured() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
