package com.theguy.app.enums;

public enum PricingType {
    FIXED("Fixed price"),
    HOURLY("Per hour"),
    NEGOTIABLE("Negotiable");
    
    private final String displayName;
    
    PricingType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() { return displayName; }
}