package com.theguy.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ProviderRegistrationDTO {
    @NotBlank(message = "Bio is required")
    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;
    
    @NotBlank(message = "Service category is required")
    private String categoryId;
    
    private String profileImageUrl;
    
    @Size(min = 3, max = 10, message = "3-10 portfolio photos required")
    private List<String> portfolioImageUrls;
    
    @Size(min = 1, message = "At least one verification document required")
    private List<VerificationDocDTO> verificationDocuments;
    
    private Double latitude;
    private Double longitude;
    
    @Data
    public static class VerificationDocDTO {
        @NotBlank(message = "Document type is required")
        private String documentType;
        
        @NotBlank(message = "Document URL is required")
        private String imageUrl;
    }
}
