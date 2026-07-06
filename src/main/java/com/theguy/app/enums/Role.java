package com.theguy.app.enums;

public enum Role {
    CUSTOMER("ROLE_CUSTOMER", "Customer"),
    PROVIDER("ROLE_PROVIDER", "Service Provider"),
    ADMIN("ROLE_ADMIN", "Administrator");
    
    private final String springRole;
    private final String displayName;
    
    Role(String springRole, String displayName) {
        this.springRole = springRole;
        this.displayName = displayName;
    }
    
    public String getSpringRole() { return springRole; }
    public String getDisplayName() { return displayName; }
}