package com.theguy.app.dto;

import com.theguy.app.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobRequestDTO {
    @NotBlank(message = "Service category is required")
    private String category;
    
    @NotBlank(message = "Job description is required")
    private String description;
    
    @NotNull(message = "Urgency is required")
    private Urgency urgency;
    
    private Double budgetMin;
    private Double budgetMax;
    
    @NotNull(message = "Location is required")
    private Location location;
    
    @Data
    public static class Location {
        @NotNull(message = "Latitude is required")
        private Double latitude;
        
        @NotNull(message = "Longitude is required")
        private Double longitude;
    }
}