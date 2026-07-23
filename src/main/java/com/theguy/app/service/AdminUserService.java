package com.theguy.app.service;

import com.theguy.app.dto.admin.RiskOverviewDTO;
import com.theguy.app.dto.admin.UserDetailDTO;
import com.theguy.app.dto.admin.UserListItemDTO;
import com.theguy.app.dto.admin.UserSummaryDTO;
import com.theguy.app.entity.RiskScore;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final JobRepository jobRepository;
    private final DisputeRepository disputeRepository;

    public UserSummaryDTO getUserSummary() {
        try {
            long totalUsers = userRepository.count();
            long totalCustomers = userRepository.count() - userRepository.findAll().stream()
                    .filter(u -> u.getRole() != Role.CUSTOMER).count();
            long totalProviders = providerRepository.count();
            long totalAdmins = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN).count();
            long verifiedCount = userRepository.findAll().stream()
                    .filter(User::isVerified).count();
            double verifiedPercentage = totalUsers > 0 ? (verifiedCount * 100.0 / totalUsers) : 0.0;

            return UserSummaryDTO.builder()
                    .totalUsers(totalUsers)
                    .totalCustomers(totalCustomers)
                    .totalProviders(totalProviders)
                    .totalAdmins(totalAdmins)
                    .verifiedPercentage(Math.round(verifiedPercentage * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching user summary", e);
            return UserSummaryDTO.builder()
                    .totalUsers(0L).totalCustomers(0L).totalProviders(0L)
                    .totalAdmins(0L).verifiedPercentage(0.0)
                    .build();
        }
    }

    public RiskOverviewDTO getRiskOverview() {
        try {
            return RiskOverviewDTO.builder()
                    .low(riskScoreRepository.countByRiskLevel("LOW"))
                    .medium(riskScoreRepository.countByRiskLevel("MEDIUM"))
                    .high(riskScoreRepository.countByRiskLevel("HIGH"))
                    .critical(riskScoreRepository.countByRiskLevel("CRITICAL"))
                    .build();
        } catch (Exception e) {
            log.error("Error fetching risk overview", e);
            return RiskOverviewDTO.builder()
                    .low(0L).medium(0L).high(0L).critical(0L)
                    .build();
        }
    }

    public Page<UserListItemDTO> getUsers(String role, String search, int page, int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            List<User> allUsers = userRepository.findAll();
            List<UserListItemDTO> filtered = allUsers.stream()
                    .filter(u -> role == null || role.isBlank()
                            || u.getRole().name().equalsIgnoreCase(role))
                    .filter(u -> search == null || search.isBlank()
                            || u.getFullName().toLowerCase().contains(search.toLowerCase())
                            || u.getEmail().toLowerCase().contains(search.toLowerCase())
                            || (u.getPhoneNumber() != null && u.getPhoneNumber().contains(search)))
                    .map(this::mapUserToListItem)
                    .collect(Collectors.toList());

            int start = Math.min(page * size, filtered.size());
            int end = Math.min((page + 1) * size, filtered.size());
            List<UserListItemDTO> pageContent = filtered.subList(start, end);

            return new PageImpl<>(pageContent, pageRequest, filtered.size());
        } catch (Exception e) {
            log.error("Error fetching users", e);
            return Page.empty();
        }
    }

    public UserDetailDTO getUserDetail(UUID userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            RiskScore riskScore = riskScoreRepository.findTopByUserIdOrderByCalculatedAtDesc(userId).orElse(null);

            Long completedJobs = jobRepository.count() > 0
                    ? jobRepository.findByCustomerId(userId).stream()
                    .filter(j -> j.getStatus() != null && j.getStatus().name().equals("COMPLETED"))
                    .count()
                    : 0L;

            Long disputesCount = disputeRepository.findAll().stream()
                    .filter(d -> d.getOpenedBy() != null && d.getOpenedBy().getId().equals(userId))
                    .count();

            return UserDetailDTO.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .phoneNumber(user.getPhoneNumber())
                    .role(user.getRole() != null ? user.getRole().name() : "UNKNOWN")
                    .isVerified(user.isVerified())
                    .avatarUrl(user.getAvatarUrl())
                    .createdAt(user.getCreatedAt())
                    .riskScore(riskScore != null ? riskScore.getScore() : null)
                    .riskLevel(riskScore != null ? riskScore.getRiskLevel() : "NONE")
                    .riskFactors(riskScore != null ? riskScore.getFactors() : null)
                    .completedJobsCount(completedJobs)
                    .disputesCount(disputesCount)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching user detail for userId={}", userId, e);
            throw e;
        }
    }

    private UserListItemDTO mapUserToListItem(User user) {
        RiskScore riskScore = riskScoreRepository.findTopByUserIdOrderByCalculatedAtDesc(user.getId()).orElse(null);
        return UserListItemDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole() != null ? user.getRole().name() : "UNKNOWN")
                .isVerified(user.isVerified())
                .avatarUrl(user.getAvatarUrl())
                .riskScore(riskScore != null ? riskScore.getScore() : null)
                .riskLevel(riskScore != null ? riskScore.getRiskLevel() : "NONE")
                .createdAt(user.getCreatedAt())
                .build();
    }
}
