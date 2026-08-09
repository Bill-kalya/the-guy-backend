package com.theguy.app.controller;

import com.theguy.app.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    /**
     * ETA (minutes) between the provider's latest location and the job pickup point.
     * Returns a raw body: {@code {eta, distance, providerId}} so the mobile tracking
     * engine can read {@code data['eta']} without unwrapping an ApiResponse envelope.
     */
    @GetMapping("/{jobId}/eta")
    public ResponseEntity<Map<String, Object>> eta(@PathVariable UUID jobId) {
        return ResponseEntity.ok(trackingService.getEta(jobId));
    }

    /**
     * Straight-line route polyline between the provider's latest location and the
     * job pickup point: {@code {polyline: [{lat, lng}...], providerId}}.
     */
    @GetMapping("/{jobId}/polyline")
    public ResponseEntity<Map<String, Object>> polyline(@PathVariable UUID jobId) {
        return ResponseEntity.ok(trackingService.getPolyline(jobId));
    }
}
