package com.theguy.app.enums;

public enum QuoteStatus {
    PENDING("Awaiting customer response"),
    ACCEPTED("Customer accepted — price locked"),
    REJECTED("Customer declined"),
    COUNTERED("Customer made counter-offer"),
    EXPIRED("Quote expired");

    private final String description;

    QuoteStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
