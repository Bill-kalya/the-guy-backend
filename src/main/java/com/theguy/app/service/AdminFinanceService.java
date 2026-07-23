package com.theguy.app.service;

import com.theguy.app.dto.admin.FinanceSummaryDTO;
import com.theguy.app.dto.admin.PendingPayoutDTO;
import com.theguy.app.dto.admin.RevenueTrendDTO;
import com.theguy.app.entity.*;
import com.theguy.app.enums.*;
import com.theguy.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminFinanceService {

    private final JobRepository jobRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PayoutRepository payoutRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final FinancialAuditLogRepository financialAuditLogRepository;
    private final TaxRecordRepository taxRecordRepository;

    public FinanceSummaryDTO getFinanceSummary() {
        try {
            Double totalGMV = jobRepository.findByStatus(JobStatus.COMPLETED).stream()
                    .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                    .sum();

            Double totalRevenue = ledgerEntryRepository.sumByAccountAndEntryType(
                    AccountCode.PLATFORM_REVENUE, EntryType.CREDIT);

            Double escrowCredit = ledgerEntryRepository.sumByAccountAndEntryType(
                    AccountCode.ESCROW, EntryType.CREDIT);
            Double escrowDebit = ledgerEntryRepository.sumByAccountAndEntryType(
                    AccountCode.ESCROW, EntryType.DEBIT);
            Double totalEscrow = escrowCredit - escrowDebit;

            Double totalTaxLiability = ledgerEntryRepository.sumByAccountAndEntryType(
                    AccountCode.TAX_LIABILITY, EntryType.CREDIT);

            Double pendingPayoutsTotal = payoutRepository.findByStatus(PayoutStatus.PENDING).stream()
                    .mapToDouble(Payout::getAmount)
                    .sum();

            Long openDisputesTotal = disputeRepository.countByStatus(DisputeStatus.OPEN);

            Double refundExposure = disputeRepository.findByStatus(DisputeStatus.OPEN).stream()
                    .mapToDouble(d -> d.getRefundAmount() != null ? d.getRefundAmount() : 0.0)
                    .sum();

            Long failedPayments = paymentRecordRepository.countByStatus(PaymentStatus.FAILED);

            return FinanceSummaryDTO.builder()
                    .totalGMV(totalGMV)
                    .totalRevenue(totalRevenue)
                    .totalEscrow(totalEscrow)
                    .totalTaxLiability(totalTaxLiability)
                    .pendingPayoutsTotal(pendingPayoutsTotal)
                    .openDisputesTotal(openDisputesTotal)
                    .refundExposure(refundExposure)
                    .failedPayments(failedPayments)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching finance summary", e);
            return FinanceSummaryDTO.builder()
                    .totalGMV(0.0).totalRevenue(0.0).totalEscrow(0.0)
                    .totalTaxLiability(0.0).pendingPayoutsTotal(0.0)
                    .openDisputesTotal(0L).refundExposure(0.0).failedPayments(0L)
                    .build();
        }
    }

    public List<RevenueTrendDTO> getRevenueTrend(int days) {
        try {
            List<RevenueTrendDTO> trend = new ArrayList<>();
            LocalDate today = LocalDate.now();

            List<Job> completedJobs = jobRepository.findByStatus(JobStatus.COMPLETED);
            List<LedgerEntry> revenueEntries = ledgerEntryRepository.findByAccountCode(AccountCode.PLATFORM_REVENUE);
            List<LedgerEntry> payoutEntries = ledgerEntryRepository.findByAccountCode(AccountCode.PAYOUT_OUTSTANDING);
            List<Payout> allPayouts = payoutRepository.findByStatus(PayoutStatus.COMPLETED);

            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

                double dayGmv = completedJobs.stream()
                        .filter(j -> j.getCompletedAt() != null
                                && !j.getCompletedAt().isBefore(dayStart)
                                && !j.getCompletedAt().isAfter(dayEnd))
                        .mapToDouble(j -> j.getFinalPrice() != null ? j.getFinalPrice() : 0.0)
                        .sum();

                double dayRevenue = revenueEntries.stream()
                        .filter(e -> e.getCreatedAt() != null
                                && !e.getCreatedAt().isBefore(dayStart)
                                && !e.getCreatedAt().isAfter(dayEnd))
                        .mapToDouble(LedgerEntry::getAmount)
                        .sum();

                double dayPayouts = allPayouts.stream()
                        .filter(p -> p.getProcessedAt() != null
                                && !p.getProcessedAt().isBefore(dayStart)
                                && !p.getProcessedAt().isAfter(dayEnd))
                        .mapToDouble(Payout::getAmount)
                        .sum();

                trend.add(RevenueTrendDTO.builder()
                        .date(date)
                        .gmv(dayGmv)
                        .revenue(dayRevenue)
                        .payouts(dayPayouts)
                        .build());
            }

            return trend;
        } catch (Exception e) {
            log.error("Error fetching revenue trend for {} days", days, e);
            return List.of();
        }
    }

    public List<PendingPayoutDTO> getPendingPayouts() {
        try {
            return payoutRepository.findByStatus(PayoutStatus.PENDING).stream()
                    .map(p -> PendingPayoutDTO.builder()
                            .payoutId(p.getId())
                            .providerId(p.getProvider().getId())
                            .providerName(p.getProvider().getUser() != null
                                    ? p.getProvider().getUser().getFullName() : "Unknown")
                            .providerEmail(p.getProvider().getUser() != null
                                    ? p.getProvider().getUser().getEmail() : "Unknown")
                            .amount(p.getAmount())
                            .method(p.getMethod() != null ? p.getMethod().name() : "UNKNOWN")
                            .createdAt(p.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching pending payouts", e);
            return List.of();
        }
    }

    public Page<LedgerEntry> getLedgerEntries(String accountCode, String entryType, int page, int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            if (accountCode != null && !accountCode.isBlank()) {
                AccountCode code = AccountCode.valueOf(accountCode.toUpperCase());
                return ledgerEntryRepository.findByAccountCode(code)
                        .stream()
                        .filter(e -> entryType == null || entryType.isBlank()
                                || e.getEntryType().name().equalsIgnoreCase(entryType))
                        .collect(Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> new org.springframework.data.domain.PageImpl<>(
                                        list.subList(Math.min(page * size, list.size()),
                                                Math.min((page + 1) * size, list.size())),
                                        pageRequest, list.size())
                        ));
            }
            return ledgerEntryRepository.findAll(pageRequest);
        } catch (Exception e) {
            log.error("Error fetching ledger entries", e);
            return Page.empty();
        }
    }

    public Page<TaxRecord> getTaxRecords(int page, int size) {
        try {
            return taxRecordRepository.findAll(PageRequest.of(page, size));
        } catch (Exception e) {
            log.error("Error fetching tax records", e);
            return Page.empty();
        }
    }

    public Page<FinancialAuditLog> getFinancialAuditTrail(int page, int size) {
        try {
            return financialAuditLogRepository.findAll(PageRequest.of(page, size));
        } catch (Exception e) {
            log.error("Error fetching financial audit trail", e);
            return Page.empty();
        }
    }
}
