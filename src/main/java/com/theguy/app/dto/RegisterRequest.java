package com.theguy.app.dto;

import com.theguy.app.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String fullName;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$", 
             message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
    private String password;
    
    private Role role = Role.CUSTOMER;
    
    private Double latitude;
    private Double longitude;
}