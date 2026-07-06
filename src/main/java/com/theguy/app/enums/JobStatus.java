package com.theguy.app.enums;

public enum JobStatus {
    REQUESTED("Requested", 0),
    MATCHING("Looking for provider", 1),
    ASSIGNED("Provider assigned", 2),
    IN_PROGRESS("Work in progress", 3),
    COMPLETED("Job completed", 4),
    CANCELLED("Cancelled", 5);
    
    private final String displayName;
    private final int order;
    
    JobStatus(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
    
    public String getDisplayName() { return displayName; }
    public int getOrder() { return order; }
    
    public boolean isActive() {
        return this == REQUESTED || this == MATCHING || this == ASSIGNED || this == IN_PROGRESS;
    }
}