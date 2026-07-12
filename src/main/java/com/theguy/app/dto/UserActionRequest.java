package com.theguy.app.dto;

import lombok.Data;

@Data
public class UserActionRequest {
    private String actionType;
    private String reason;
    private String userType; // PROVIDER, CUSTOMER
}

