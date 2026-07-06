package com.theguy.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceEstimateDTO {
    private Double minPrice;
    private Double maxPrice;
    private Double recommendedPrice;
    private String currency;
    private String breakdown;
    private boolean isDynamic;
}
