package com.theguy.app.controller;

import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.User;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.service.LocationService;
import com.theguy.app.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final LocationService locationService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    private final ConcurrentHashMap<UUID, LocalDateTime> lastUpdatePerProvider = new ConcurrentHashMap<>();

    private static final Duration MIN_UPDATE_INTERVAL = Duration.ofMillis(500);

    private Provider resolveProvider(String principalName) {
        String email = principalName;
        try {
            UUID uuid = UUID.fromString(principalName);
            return providerRepository.findByUserId(uuid)
                    .orElseThrow(() -> new IllegalStateException("Provider profile not found"));
        } catch (IllegalArgumentException e) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            return providerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Provider profile not found"));
        }
    }

    /**
     * Provider sends location update every 3-5 seconds
     * Client: /app/location/update
     */
    @MessageMapping("/location/update")
    public void handleLocationUpdate(
            @Payload LocationUpdatePayload payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Location update rejected: unauthenticated");
            return;
        }

        UUID providerId = resolveProvider(principal.getName()).getId();

        if (payload.getLatitude() == null || payload.getLongitude() == null) {
            log.warn("Location update rejected for {}: missing coordinates", providerId);
            return;
        }

        if (payload.getLatitude() < -90 || payload.getLatitude() > 90 ||
            payload.getLongitude() < -180 || payload.getLongitude() > 180) {
            log.warn("Location update rejected for {}: invalid coordinates", providerId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdate = lastUpdatePerProvider.get(providerId);
        if (lastUpdate != null && Duration.between(lastUpdate, now).compareTo(MIN_UPDATE_INTERVAL) < 0) {
            log.debug("Rate-limited location update from provider {}", providerId);
            return;
        }
        lastUpdatePerProvider.put(providerId, now);

        locationService.updateLocation(
            providerId,
            payload.getLatitude(),
            payload.getLongitude(),
            payload.getHeading(),
            payload.getSpeed()
        );

        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("providerId", providerId);
        broadcast.put("latitude", payload.getLatitude());
        broadcast.put("longitude", payload.getLongitude());
        broadcast.put("heading", payload.getHeading());
        broadcast.put("speed", payload.getSpeed());
        broadcast.put("timestamp", now.toString());

        notificationService.broadcastToTopic(
            "provider/" + providerId + "/location",
            broadcast
        );

        log.debug("Provider {} location updated via WebSocket", providerId);
    }

    /**
     * Customer requests location tracking for a specific provider
     * Client: /app/location/track/{providerId}
     */
    @MessageMapping("/location/track/{providerId}")
    public void handleTrackRequest(
            @DestinationVariable UUID providerId,
            SimpMessageHeaderAccessor headerAccessor) {

        if (headerAccessor.getUser() == null) {
            log.warn("Unauthenticated tracking request for provider {}", providerId);
            return;
        }

        String principalName = headerAccessor.getUser().getName();

        boolean isAdmin = headerAccessor.getUser() instanceof org.springframework.security.core.Authentication auth
                && auth.getAuthorities().stream()
                    .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        boolean allowed = isAdmin;
        if (!allowed) {
            User user = userRepository.findByEmail(principalName).orElse(null);
            if (user == null) {
                log.warn("Tracking request for provider {} from unknown user {}", providerId, principalName);
                return;
            }
            allowed = locationService.canTrack(user.getId(), providerId);
        }

        if (!allowed) {
            log.warn("Tracking request denied: user {} has no active job with provider {}",
                principalName, providerId);
            return;
        }

        String customerId = principalName;

        ProviderLocation location = locationService.getProviderLocation(providerId);

        if (location != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("providerId", providerId);
            response.put("latitude", location.getLatitude());
            response.put("longitude", location.getLongitude());
            response.put("heading", location.getHeading());
            response.put("speed", location.getSpeed());
            response.put("timestamp", location.getUpdatedAt().toString());

            notificationService.sendToUser(
                customerId,
                "/queue/location/track/" + providerId,
                response
            );
        }

        log.debug("Customer {} tracking provider {}", customerId, providerId);
    }

    @Data
    public static class LocationUpdatePayload {
        private Double latitude;
        private Double longitude;
        private Double heading;
        private Double speed;
    }
}
