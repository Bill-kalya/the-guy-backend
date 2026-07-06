package com.theguy.app.dto;

import lombok.Data;

@Data
public class MpesaRequest {
    private String phoneNumber;
    private Double amount;
}