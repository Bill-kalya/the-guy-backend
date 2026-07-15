package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchProvidersResponse {
    private String query;
    private int totalResults;
    private List<SearchProviderItem> providers;
}
