package com.theguy.app.enums;

public enum PricingType {
    CATALOG("Fixed-price catalog"),
    QUOTE_REQUIRED("Quote required"),
    PLATFORM_CALCULATED("Platform-calculated");

    private final String displayName;

    PricingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}