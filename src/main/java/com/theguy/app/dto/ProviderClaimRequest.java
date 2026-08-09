package com.theguy.app.dto;

import lombok.Data;

@Data
public class ProviderClaimRequest {
    private String phoneNumber;
    private String claimCode;
}
