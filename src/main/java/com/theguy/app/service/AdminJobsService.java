package com.theguy.app.service;

import com.theguy.app.dto.admin.JobListItemDTO;
import com.theguy.app.dto.admin.JobSummaryDTO;
import com.theguy.app.entity.Job;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminJobsService {

    private final JobRepository jobRepository;

    public JobSummaryDTO getJobSummary() {
        try {
            List<Job> allJobs = jobRepository.findAll();
            long total = allJobs.size();
            long completed = allJobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
            long cancelled = allJobs.stream().filter(j -> j.getStatus() == JobStatus.CANCELLED).count();
            long active = allJobs.stream().filter(j ->
                j.getStatus() == JobStatus.IN_PROGRESS ||
                j.getStatus() == JobStatus.ASSIGNED ||
                j.getStatus() == JobStatus.MATCHING ||
                j.getStatus() == JobStatus.REQUESTED
            ).count();

            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
            long jobsToday = allJobs.stream()
                .filter(j -> j.getCreatedAt() != null && !j.getCreatedAt().isBefore(todayStart) && !j.getCreatedAt().isAfter(todayEnd))
                .count();

            double gmvToday = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .filter(j -> j.getCompletedAt() != null && !j.getCompletedAt().isBefore(todayStart) && !j.getCompletedAt().isAfter(todayEnd))
                .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                .sum();

            double completionRate = total > 0 ? (double) completed / total * 100 : 0.0;

            double avgValue = allJobs.stream()
                .filter(j -> j.getFinalPrice() != null && j.getStatus() == JobStatus.COMPLETED)
                .mapToDouble(Job::getFinalPrice)
                .average()
                .orElse(0.0);

            return JobSummaryDTO.builder()
                .totalJobs((long) total)
                .activeJobs(active)
                .completedJobs(completed)
                .cancelledJobs(cancelled)
                .disputedJobs(0L)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .avgJobValue(Math.round(avgValue * 100.0) / 100.0)
                .jobsToday(jobsToday)
                .gmvToday(Math.round(gmvToday * 100.0) / 100.0)
                .build();
        } catch (Exception e) {
            log.error("Error fetching job summary", e);
            return JobSummaryDTO.builder()
                .totalJobs(0L).activeJobs(0L).completedJobs(0L)
                .cancelledJobs(0L).disputedJobs(0L)
                .completionRate(0.0).avgJobValue(0.0)
                .jobsToday(0L).gmvToday(0.0)
                .build();
        }
    }

    public Page<JobListItemDTO> getJobs(String status, String search, int page, int size) {
        try {
            List<Job> allJobs = jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

            List<JobListItemDTO> filtered = allJobs.stream()
                .filter(j -> status == null || status.isBlank() || j.getStatus().name().equalsIgnoreCase(status))
                .filter(j -> search == null || search.isBlank() ||
                    (j.getServiceCategory() != null && j.getServiceCategory().toLowerCase().contains(search.toLowerCase())) ||
                    (j.getDescription() != null && j.getDescription().toLowerCase().contains(search.toLowerCase())) ||
                    (j.getCustomer() != null && j.getCustomer().getFullName() != null && j.getCustomer().getFullName().toLowerCase().contains(search.toLowerCase())) ||
                    (j.getProvider() != null && j.getProvider().getUser() != null && j.getProvider().getUser().getFullName() != null && j.getProvider().getUser().getFullName().toLowerCase().contains(search.toLowerCase()))
                )
                .map(this::mapToListItem)
                .collect(Collectors.toList());

            int start = page * size;
            int end = Math.min(start + size, filtered.size());
            List<JobListItemDTO> paged = start < filtered.size() ? filtered.subList(start, end) : List.of();

            return new PageImpl<>(paged, PageRequest.of(page, size), filtered.size());
        } catch (Exception e) {
            log.error("Error fetching jobs", e);
            return Page.empty();
        }
    }

    private JobListItemDTO mapToListItem(Job job) {
        return JobListItemDTO.builder()
            .id(job.getId())
            .serviceCategory(job.getServiceCategory())
            .description(job.getDescription())
            .status(job.getStatus() != null ? job.getStatus().name() : "UNKNOWN")
            .urgency(job.getUrgency() != null ? job.getUrgency().name() : null)
            .customerName(job.getCustomer() != null ? job.getCustomer().getFullName() : null)
            .customerEmail(job.getCustomer() != null ? job.getCustomer().getEmail() : null)
            .providerName(job.getProvider() != null && job.getProvider().getUser() != null
                ? job.getProvider().getUser().getFullName() : null)
            .providerEmail(job.getProvider() != null && job.getProvider().getUser() != null
                ? job.getProvider().getUser().getEmail() : null)
            .finalPrice(job.getFinalPrice())
            .priceEstimateMin(job.getPriceEstimateMin())
            .priceEstimateMax(job.getPriceEstimateMax())
            .createdAt(job.getCreatedAt())
            .completedAt(job.getCompletedAt())
            .build();
    }
}
