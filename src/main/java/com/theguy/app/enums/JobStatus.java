package com.theguy.app.enums;

public enum JobStatus {
    REQUESTED("Requested", 0),
    MATCHING("Looking for provider", 1),
    ASSIGNED("Provider assigned", 2),
    ON_THE_WAY("Provider on the way", 3),
    ARRIVED("Provider arrived", 4),
    IN_PROGRESS("Work in progress", 5),
    AWAITING_CUSTOMER_CONFIRMATION("Awaiting your confirmation", 6),
    COMPLETED("Job completed", 7),
    DISPUTED("Disputed", 8),
    CANCELLED("Cancelled", 9);

    private final String displayName;
    private final int order;

    JobStatus(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    public String getDisplayName() { return displayName; }
    public int getOrder() { return order; }

    public boolean isActive() {
        return this == REQUESTED || this == MATCHING || this == ASSIGNED
            || this == ON_THE_WAY || this == ARRIVED || this == IN_PROGRESS
            || this == AWAITING_CUSTOMER_CONFIRMATION;
    }
}