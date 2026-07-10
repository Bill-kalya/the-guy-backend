package com.theguy.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_locations", indexes = {
    @Index(name = "idx_provider_location_provider", columnList = "provider_id"),
    @Index(name = "idx_provider_location_lat", columnList = "latitude"),
    @Index(name = "idx_provider_location_lng", columnList = "longitude")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderLocation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID providerId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private LocalDateTime updatedAt;

    private Double heading;  // Optional: direction in degrees

    private Double speed;    // Optional: speed in m/s

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}