package com.theguy.app.service;

import com.theguy.app.entity.FcmToken;
import com.theguy.app.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;

    public void registerToken(UUID userId, String token, String platform) {
        fcmTokenRepository.findAllByUserId(userId).stream()
            .filter(t -> t.getToken().equals(token))
            .findFirst()
            .ifPresentOrElse(existing -> {
                existing.setLastUsedAt(LocalDateTime.now());
                fcmTokenRepository.save(existing);
            }, () -> {
                FcmToken fcmToken = new FcmToken();
                fcmToken.setUserId(userId);
                fcmToken.setToken(token);
                fcmToken.setDevicePlatform(platform);
                fcmToken.setLastUsedAt(LocalDateTime.now());
                fcmTokenRepository.save(fcmToken);
                log.info("Registered new FCM token for user {} (platform={})", userId, platform);
            });
    }

    public void removeToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }
}
