package com.theguy.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProviderProfileUpdateRequest {

    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;

    private String profileImageUrl;
}
