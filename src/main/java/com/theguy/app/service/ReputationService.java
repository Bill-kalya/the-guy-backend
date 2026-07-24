package com.theguy.app.service;

import com.theguy.app.dto.ReputationDTO;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderReputation;
import com.theguy.app.entity.ProviderStatistics;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.ProviderReputationRepository;
import com.theguy.app.repository.ProviderStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    private final ProviderReputationRepository reputationRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStatisticsRepository providerStatisticsRepository;

    @Transactional
    public ProviderReputation calculate(UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        Optional<ProviderStatistics> statsOpt = providerStatisticsRepository.findById(providerId);

        double sqsScore = statsOpt.map(ProviderStatistics::getSqs).orElse(0.0);

        double totalJobs = provider.getJobsCompleted() + provider.getJobsCancelled();
        double completionScore = totalJobs > 0 ? (provider.getJobsCompleted() / totalJobs) * 100 : 0;
        double responseScore = provider.getResponseRate() * 100;
        double consistencyBonus = provider.getRepeatClientsPercentage();

        double sqsContribution = sqsScore * 0.40;
        double completionContribution = completionScore * 0.20;
        double responseContribution = responseScore * 0.15;
        double consistencyContrib = consistencyBonus * 0.05;

        double cancellationPenalty = totalJobs > 0
                ? (provider.getJobsCancelled() / totalJobs) * 100 * 0.10
                : 0;

        double rawScore = sqsContribution + completionContribution + responseContribution
                + consistencyContrib - cancellationPenalty;
        int finalScore = (int) Math.min(100, Math.max(0, Math.round(rawScore)));

        String tier;
        if (finalScore >= 90) tier = "ELITE";
        else if (finalScore >= 75) tier = "PREMIUM";
        else if (finalScore >= 50) tier = "STANDARD";
        else tier = "NEW";

        ProviderReputation rep = reputationRepository.findById(providerId)
                .orElse(new ProviderReputation());
        rep.setProviderId(providerId);
        rep.setScore(finalScore);
        rep.setTier(tier);
        rep.setSqsContribution(sqsContribution);
        rep.setCompletionContribution(completionContribution);
        rep.setResponseContribution(responseContribution);
        rep.setConsistencyBonus(consistencyContrib);
        rep.setCancellationPenalty(-cancellationPenalty);
        rep.setDisputePenalty(0.0);
        rep.setCalculatedAt(LocalDateTime.now());

        return reputationRepository.save(rep);
    }

    @Transactional(readOnly = true)
    public Optional<ProviderReputation> getReputation(UUID providerId) {
        return reputationRepository.findById(providerId);
    }

    @Transactional(readOnly = true)
    public ReputationDTO getReputationDTO(UUID providerId) {
        ProviderReputation rep = reputationRepository.findById(providerId)
                .orElseGet(() -> calculate(providerId));

        return ReputationDTO.builder()
                .score(rep.getScore())
                .tier(rep.getTier())
                .sqsContribution(rep.getSqsContribution())
                .completionContribution(rep.getCompletionContribution())
                .responseContribution(rep.getResponseContribution())
                .consistencyBonus(rep.getConsistencyBonus())
                .cancellationPenalty(rep.getCancellationPenalty())
                .disputePenalty(rep.getDisputePenalty())
                .build();
    }
}
