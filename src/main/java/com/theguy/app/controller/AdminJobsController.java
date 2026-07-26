package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.dto.admin.JobListItemDTO;
import com.theguy.app.dto.admin.JobSummaryDTO;
import com.theguy.app.service.AdminJobsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminJobsController {

    private final AdminJobsService adminJobsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<JobSummaryDTO>> getJobSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminJobsService.getJobSummary()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobListItemDTO>>> getJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminJobsService.getJobs(status, search, page, size)));
    }
}
