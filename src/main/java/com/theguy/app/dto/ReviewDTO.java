package com.theguy.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class ReviewDTO {
    @NotNull(message = "Job ID is required")
    private UUID jobId;
    
    private UUID providerId;
    
    @Min(value = 1, message = "Quality rating must be between 1 and 5")
    @Max(value = 5, message = "Quality rating must be between 1 and 5")
    private Integer ratingQuality;
    
    @Min(value = 1, message = "Reliability rating must be between 1 and 5")
    @Max(value = 5, message = "Reliability rating must be between 1 and 5")
    private Integer ratingReliability;
    
    @Min(value = 1, message = "Communication rating must be between 1 and 5")
    @Max(value = 5, message = "Communication rating must be between 1 and 5")
    private Integer ratingCommunication;
    
    @NotBlank(message = "Review comment is required")
    private String comment;
}