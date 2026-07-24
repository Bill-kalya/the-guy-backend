package com.theguy.app.exception;

import java.util.UUID;

public class ProviderNotFoundException extends RuntimeException {
    private final UUID providerId;

    public ProviderNotFoundException(UUID providerId) {
        super("Provider not found: " + providerId);
        this.providerId = providerId;
    }

    public UUID getProviderId() {
        return providerId;
    }
}
