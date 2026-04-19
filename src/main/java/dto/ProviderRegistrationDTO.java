package com.theguy.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ProviderRegistrationDTO {
    @NotBlank(message = "Bio is required")
    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;
    
    private String profileImageUrl;
    
    @NotNull(message = "At least one service is required")
    @Size(min = 1, message = "At least one service is required")
    private List<ServiceDTO> services;
    
    @Data
    public static class ServiceDTO {
        @NotBlank(message = "Service category is required")
        private String category;
        
        @NotBlank(message = "Service title is required")
        private String title;
        
        private String description;
        private String pricingType;
        private Double basePrice;
    }
}