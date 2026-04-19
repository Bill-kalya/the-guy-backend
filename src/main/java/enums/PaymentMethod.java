package com.theguy.app.enums;

public enum PaymentMethod {
    MPESA("M-Pesa", true),
    CARD("Card Payment", false),
    CASH("Cash", true);
    
    private final String displayName;
    private final boolean requiresMobileNumber;
    
    PaymentMethod(String displayName, boolean requiresMobileNumber) {
        this.displayName = displayName;
        this.requiresMobileNumber = requiresMobileNumber;
    }
    
    public String getDisplayName() { return displayName; }
    public boolean requiresMobileNumber() { return requiresMobileNumber; }
}