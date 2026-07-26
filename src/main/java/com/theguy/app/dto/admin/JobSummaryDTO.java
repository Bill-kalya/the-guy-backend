package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobSummaryDTO {
    private Long totalJobs;
    private Long activeJobs;
    private Long completedJobs;
    private Long cancelledJobs;
    private Long disputedJobs;
    private Double completionRate;
    private Double avgJobValue;
    private Long jobsToday;
    private Double gmvToday;
}
