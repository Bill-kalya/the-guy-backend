package com.theguy.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;
    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;
    private String avatarUrl;
}