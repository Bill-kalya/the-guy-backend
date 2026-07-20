package com.theguy.app.controller;

import com.theguy.app.entity.ProviderLocation;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final LocationService locationService;
    private final NotificationService notificationService;

    /**
     * Provider sends location update every 3-5 seconds
     * Client: /app/location/update
     */
    @MessageMapping("/location/update")
    public void handleLocationUpdate(
            @Payload LocationUpdatePayload payload,
            Principal principal) {

        // Extract provider ID from auth token
        String providerIdStr = principal.getName();
        UUID providerId = UUID.fromString(providerIdStr);

        // Save location to database
        locationService.updateLocation(
            providerId,
            payload.getLatitude(),
            payload.getLongitude(),
            payload.getHeading(),
            payload.getSpeed()
        );

        // Broadcast to all customers watching this provider
        // (They subscribe to /topic/provider/{providerId}/location)
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("providerId", providerId);
        broadcast.put("latitude", payload.getLatitude());
        broadcast.put("longitude", payload.getLongitude());
        broadcast.put("heading", payload.getHeading());
        broadcast.put("speed", payload.getSpeed());
        broadcast.put("timestamp", LocalDateTime.now().toString());

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

        // Get customer ID from auth
        if (headerAccessor.getUser() == null) {
            log.warn("Unauthenticated tracking request for provider {}", providerId);
            return;
        }

        String customerId = headerAccessor.getUser().getName();

        // Get current location of provider
        ProviderLocation location = locationService.getProviderLocation(providerId);

        if (location != null) {
            // Send initial location to customer
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