package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.SearchProviderItem;
import com.theguy.app.dto.SearchProvidersResponse;
import com.theguy.app.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void shouldSearchProviders() throws Exception {
        UUID providerId = UUID.randomUUID();
        SearchProvidersResponse response = SearchProvidersResponse.builder()
            .query("plumber")
            .totalResults(1)
            .providers(List.of(
                SearchProviderItem.builder()
                    .id(providerId)
                    .businessName("John Plumbing")
                    .distance(1200.0)
                    .etaMinutes(8)
                    .serviceQualityScore(94.0)
                    .verified(true)
                    .rating(4.8)
                    .completedJobs(321)
                    .build()
            ))
            .build();

        when(searchService.searchProviders(eq("plumber"), eq(-0.0917), eq(34.7680), eq(10000.0), eq(0), eq(20)))
            .thenReturn(response);

        mockMvc.perform(get("/api/search/providers")
                .param("query", "plumber")
                .param("lat", "-0.0917")
                .param("lng", "34.7680")
                .param("radius", "10000")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.query").value("plumber"))
            .andExpect(jsonPath("$.data.totalResults").value(1))
            .andExpect(jsonPath("$.data.providers[0].businessName").value("John Plumbing"));
    }
}
