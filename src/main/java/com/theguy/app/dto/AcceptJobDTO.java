package com.theguy.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class AcceptJobDTO {
    @NotNull(message = "Job ID is required")
    private UUID jobId;
    
    private Double proposedPrice;
    private String notes;
}