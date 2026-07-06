package com.theguy.app.enums;

public enum PaymentStatus {
    PENDING("Payment pending"),
    HELD("Payment held in escrow"),
    RELEASED("Released to provider"),
    REFUNDED("Refunded to customer"),
    FAILED("Payment failed");
    
    private final String description;
    
    PaymentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}