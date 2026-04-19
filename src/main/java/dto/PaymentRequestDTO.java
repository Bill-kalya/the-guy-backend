package com.theguy.app.dto;

import com.theguy.app.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.UUID;

@Data
public class PaymentRequestDTO {
    @NotNull(message = "Job ID is required")
    private UUID jobId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Double amount;
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    
    private String mpesaNumber;
    private String cardToken;
}