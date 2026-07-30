package com.theguy.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectCompletionDTO {
    @NotBlank(message = "Reason is required")
    private String reason;
}