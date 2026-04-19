package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PriceEstimateDTO {
    private Double minPrice;
    private Double maxPrice;
    private Double recommendedPrice;
    private String currency;
    private String breakdown;
    private boolean isDynamic;
    
    public PriceEstimateDTO(Double min, Double max) {
        this.minPrice = min;
        this.maxPrice = max;
        this.recommendedPrice = (min + max) / 2;
        this.currency = "KES";
        this.isDynamic = true;
    }
}