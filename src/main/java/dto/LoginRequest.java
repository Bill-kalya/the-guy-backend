package com.theguy.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?254[0-9]{9}$", message = "Invalid Kenyan phone number")
    private String phoneNumber;
    
    @NotBlank(message = "Password is required")
    private String password;
}