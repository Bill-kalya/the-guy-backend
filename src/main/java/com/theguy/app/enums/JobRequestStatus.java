package com.theguy.app.enums;

public enum JobRequestStatus {
    PENDING("Waiting for response"),
    ACCEPTED("Provider accepted"),
    REJECTED("Provider declined"),
    EXPIRED("No response");
    
    private final String description;
    
    JobRequestStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}