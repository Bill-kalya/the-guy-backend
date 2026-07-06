package com.theguy.app.enums;

public enum VerificationLevel {
    NONE(0, "Unverified", false),
    BASIC(1, "Phone & Email Verified", true),
    ID_VERIFIED(2, "ID Verified", true),
    BUSINESS(3, "Business Verified", true);
    
    private final int level;
    private final String displayName;
    private final boolean canAcceptJobs;
    
    VerificationLevel(int level, String displayName, boolean canAcceptJobs) {
        this.level = level;
        this.displayName = displayName;
        this.canAcceptJobs = canAcceptJobs;
    }
    
    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public boolean canAcceptJobs() { return canAcceptJobs; }
}