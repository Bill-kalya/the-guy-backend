package com.theguy.app.service;

import com.theguy.app.dto.admin.ProviderDetailDTO;
import com.theguy.app.dto.admin.ProviderFinancialsDTO;
import com.theguy.app.dto.admin.ProviderListItemDTO;
import com.theguy.app.dto.admin.ProviderPerformanceDTO;
import com.theguy.app.dto.admin.ProviderSummaryDTO;
import com.theguy.app.entity.*;
import com.theguy.app.enums.PayoutStatus;
import com.theguy.app.enums.VerificationLevel;
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
public class AdminProviderService {

    private final ProviderRepository providerRepository;
    private final WalletRepository walletRepository;
    private final PayoutRepository payoutRepository;
    private final JobRepository jobRepository;
    private final DisputeRepository disputeRepository;
    private final RiskScoreRepository riskScoreRepository;

    public ProviderSummaryDTO getProviderSummary() {
        try {
            long totalProviders = providerRepository.count();
            long onlineNow = providerRepository.findByIsOnlineTrue().size();
            long pendingVerification = providerRepository.findAll().stream()
                    .filter(p -> p.getVerificationLevel() == VerificationLevel.NONE
                            || p.getVerificationLevel() == VerificationLevel.BASIC)
                    .count();
            Double avgRating = providerRepository.findAll().stream()
                    .mapToDouble(Provider::getRatingAvg)
                    .average()
                    .orElse(0.0);

            return ProviderSummaryDTO.builder()
                    .totalProviders(totalProviders)
                    .onlineNow(onlineNow)
                    .pendingVerification(pendingVerification)
                    .avgRating(Math.round(avgRating * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching provider summary", e);
            return ProviderSummaryDTO.builder()
                    .totalProviders(0L).onlineNow(0L)
                    .pendingVerification(0L).avgRating(0.0)
                    .build();
        }
    }

    public Page<ProviderListItemDTO> getProviders(String verification, String status, Double minRating,
                                                   String search, int page, int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            List<Provider> allProviders = providerRepository.findAll();

            List<ProviderListItemDTO> filtered = allProviders.stream()
                    .filter(p -> verification == null || verification.isBlank()
                            || p.getVerificationLevel().name().equalsIgnoreCase(verification))
                    .filter(p -> status == null || status.isBlank()
                            || ("online".equalsIgnoreCase(status) && p.isOnline())
                            || ("offline".equalsIgnoreCase(status) && !p.isOnline()))
                    .filter(p -> minRating == null || p.getRatingAvg() >= minRating)
                    .filter(p -> search == null || search.isBlank()
                            || (p.getUser() != null && (
                                    p.getUser().getFullName().toLowerCase().contains(search.toLowerCase())
                                    || p.getUser().getEmail().toLowerCase().contains(search.toLowerCase())))
                            || (p.getBio() != null && p.getBio().toLowerCase().contains(search.toLowerCase())))
                    .map(this::mapProviderToListItem)
                    .collect(Collectors.toList());

            int start = Math.min(page * size, filtered.size());
            int end = Math.min((page + 1) * size, filtered.size());
            List<ProviderListItemDTO> pageContent = filtered.subList(start, end);

            return new PageImpl<>(pageContent, pageRequest, filtered.size());
        } catch (Exception e) {
            log.error("Error fetching providers", e);
            return Page.empty();
        }
    }

    public ProviderDetailDTO getProviderDetail(UUID providerId) {
        try {
            Provider provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

            Wallet wallet = walletRepository.findByProvider_Id(providerId).orElse(null);
            RiskScore riskScore = riskScoreRepository.findTopByUserIdOrderByCalculatedAtDesc(
                    provider.getUser() != null ? provider.getUser().getId() : null).orElse(null);
            long openDisputes = disputeRepository.findAll().stream()
                    .filter(d -> d.getJob() != null
                            && d.getJob().getProvider() != null
                            && d.getJob().getProvider().getId().equals(providerId))
                    .filter(d -> d.getStatus() != null && d.getStatus().name().equals("OPEN"))
                    .count();

            return ProviderDetailDTO.builder()
                    .id(provider.getId())
                    .userId(provider.getUser() != null ? provider.getUser().getId() : null)
                    .fullName(provider.getUser() != null ? provider.getUser().getFullName() : "Unknown")
                    .email(provider.getUser() != null ? provider.getUser().getEmail() : "Unknown")
                    .phoneNumber(provider.getUser() != null ? provider.getUser().getPhoneNumber() : "Unknown")
                    .bio(provider.getBio())
                    .profileImageUrl(provider.getProfileImageUrl())
                    .verificationLevel(provider.getVerificationLevel() != null
                            ? provider.getVerificationLevel().name() : "NONE")
                    .isOnline(provider.isOnline())
                    .ratingAvg(provider.getRatingAvg())
                    .totalReviews(provider.getTotalReviews())
                    .jobsCompleted(provider.getJobsCompleted())
                    .jobsCancelled(provider.getJobsCancelled())
                    .responseRate(provider.getResponseRate())
                    .repeatClientsPercentage(provider.getRepeatClientsPercentage())
                    .dynamicPriceMultiplier(provider.getDynamicPriceMultiplier())
                    .lastActiveAt(provider.getLastActiveAt())
                    .createdAt(provider.getCreatedAt())
                    .pendingBalance(wallet != null ? wallet.getPendingBalance() : 0.0)
                    .availableBalance(wallet != null ? wallet.getAvailableBalance() : 0.0)
                    .walletCurrency(wallet != null ? wallet.getCurrency() : "KES")
                    .riskScore(riskScore != null ? riskScore.getScore() : null)
                    .riskLevel(riskScore != null ? riskScore.getRiskLevel() : "NONE")
                    .openDisputesCount(openDisputes)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching provider detail for providerId={}", providerId, e);
            throw e;
        }
    }

    public ProviderFinancialsDTO getProviderFinancials(UUID providerId) {
        try {
            Provider provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

            Wallet wallet = walletRepository.findByProvider_Id(providerId).orElse(null);
            Double totalEarnings = jobRepository.getTotalEarningsByProvider(providerId);
            List<Payout> allPayouts = payoutRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
            double totalWithdrawals = allPayouts.stream()
                    .filter(p -> p.getStatus() == PayoutStatus.COMPLETED)
                    .mapToDouble(Payout::getAmount)
                    .sum();
            long pendingPayoutCount = allPayouts.stream()
                    .filter(p -> p.getStatus() == PayoutStatus.PENDING)
                    .count();

            return ProviderFinancialsDTO.builder()
                    .pendingBalance(wallet != null ? wallet.getPendingBalance() : 0.0)
                    .availableBalance(wallet != null ? wallet.getAvailableBalance() : 0.0)
                    .currency(wallet != null ? wallet.getCurrency() : "KES")
                    .totalEarnings(totalEarnings != null ? totalEarnings : 0.0)
                    .totalWithdrawals(totalWithdrawals)
                    .pendingPayoutCount(pendingPayoutCount)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching provider financials for providerId={}", providerId, e);
            throw e;
        }
    }

    public ProviderPerformanceDTO getProviderPerformance(UUID providerId) {
        try {
            Provider provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

            Long completedJobs = jobRepository.countCompletedByProvider(providerId);
            long cancelledJobs = provider.getJobsCancelled();
            double cancellationRate = (completedJobs + cancelledJobs) > 0
                    ? (cancelledJobs * 100.0 / (completedJobs + cancelledJobs))
                    : 0.0;

            return ProviderPerformanceDTO.builder()
                    .ratingAvg(provider.getRatingAvg())
                    .totalReviews(provider.getTotalReviews())
                    .completedJobs(completedJobs)
                    .cancelledJobs(cancelledJobs)
                    .cancellationRate(Math.round(cancellationRate * 100.0) / 100.0)
                    .responseRate(provider.getResponseRate())
                    .repeatClientsPercentage(provider.getRepeatClientsPercentage())
                    .build();
        } catch (Exception e) {
            log.error("Error fetching provider performance for providerId={}", providerId, e);
            throw e;
        }
    }

    private ProviderListItemDTO mapProviderToListItem(Provider provider) {
        return ProviderListItemDTO.builder()
                .id(provider.getId())
                .userId(provider.getUser() != null ? provider.getUser().getId() : null)
                .fullName(provider.getUser() != null ? provider.getUser().getFullName() : "Unknown")
                .email(provider.getUser() != null ? provider.getUser().getEmail() : "Unknown")
                .phoneNumber(provider.getUser() != null ? provider.getUser().getPhoneNumber() : "Unknown")
                .bio(provider.getBio())
                .profileImageUrl(provider.getProfileImageUrl())
                .verificationLevel(provider.getVerificationLevel() != null
                        ? provider.getVerificationLevel().name() : "NONE")
                .isOnline(provider.isOnline())
                .ratingAvg(provider.getRatingAvg())
                .totalReviews(provider.getTotalReviews())
                .jobsCompleted(provider.getJobsCompleted())
                .jobsCancelled(provider.getJobsCancelled())
                .lastActiveAt(provider.getLastActiveAt())
                .createdAt(provider.getCreatedAt())
                .build();
    }
}
