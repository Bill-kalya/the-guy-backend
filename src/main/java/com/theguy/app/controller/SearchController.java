package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.SearchProvidersResponse;
import com.theguy.app.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<SearchProvidersResponse>> searchProviders(
            @RequestParam String query,
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lng,
            @RequestParam(defaultValue = "10000") @Min(100) @Max(50000) double radius,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        SearchProvidersResponse response = searchService.searchProviders(query, lat, lng, radius, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> suggestions(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(searchService.getSuggestions(q)));
    }
}
