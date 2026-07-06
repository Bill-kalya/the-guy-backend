package com.theguy.app.enums;

public enum Urgency {
    INSTANT(5, "Now", 5000),      // 5km radius, immediate
    SCHEDULED(15, "Later", 15000); // 15km radius, flexible
    
    private final int radiusKm;
    private final String displayName;
    private final int multiplier; // meters
    
    Urgency(int radiusKm, String displayName, int multiplier) {
        this.radiusKm = radiusKm;
        this.displayName = displayName;
        this.multiplier = multiplier;
    }
    
    public int getRadiusKm() { return radiusKm; }
    public String getDisplayName() { return displayName; }
    public int getMultiplier() { return multiplier; }
}