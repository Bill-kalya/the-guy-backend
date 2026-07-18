package com.theguy.app.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Role {
    CUSTOMER("ROLE_CUSTOMER", "customer", "Customer"),
    PROVIDER("ROLE_PROVIDER", "provider", "Service Provider"),
    ADMIN("ROLE_ADMIN", "admin", "Administrator");
    
    private final String springRole;
    private final String apiRole;
    private final String displayName;
    
    Role(String springRole, String apiRole, String displayName) {
        this.springRole = springRole;
        this.apiRole = apiRole;
        this.displayName = displayName;
    }
    
    public String getSpringRole() { return springRole; }
    
    @JsonValue
    public String getApiRole() { return apiRole; }
    
    public String getDisplayName() { return displayName; }
    
    @JsonCreator
    public static Role fromApiRole(String apiRole) {
        if (apiRole == null) return CUSTOMER;
        return Arrays.stream(values())
            .filter(r -> r.apiRole.equals(apiRole) || r.name().equalsIgnoreCase(apiRole))
            .findFirst()
            .orElse(CUSTOMER);
    }
}