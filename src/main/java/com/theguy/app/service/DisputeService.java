package com.theguy.app.service;

import com.theguy.app.entity.Dispute;
import com.theguy.app.entity.Job;
import com.theguy.app.entity.User;
import com.theguy.app.enums.DisputeStatus;
import com.theguy.app.enums.FinancialAction;
import com.theguy.app.enums.AuditActorType;
import com.theguy.app.repository.DisputeRepository;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final FinancialAuditLogService auditLogService;

    @Transactional
    public Dispute openDispute(UUID jobId, UUID userId, String reason) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (disputeRepository.findByJobId(jobId).isPresent()) {
            throw new IllegalStateException("Dispute already exists for this job");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Dispute dispute = Dispute.builder()
                .job(job)
                .openedBy(user)
                .reason(reason)
                .status(DisputeStatus.OPEN)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Dispute opened: jobId={}, userId={}, reason={}", jobId, userId, reason);

        auditLogService.log(userId, AuditActorType.USER, FinancialAction.DISPUTE_OPENED,
                "Dispute", saved.getId(), "Dispute opened for job " + jobId);

        return saved;
    }

    @Transactional
    public Dispute investigate(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));
        dispute.setStatus(DisputeStatus.INVESTIGATING);
        Dispute saved = disputeRepository.save(dispute);

        auditLogService.log(null, AuditActorType.ADMIN, FinancialAction.DISPUTE_OPENED,
                "Dispute", disputeId, "Dispute under investigation");

        return saved;
    }

    @Transactional
    public Dispute resolve(UUID disputeId, Double refundAmount, Double providerPenalty, String notes) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setRefundAmount(refundAmount);
        dispute.setProviderPenalty(providerPenalty);
        dispute.setResolutionNotes(notes);
        dispute.setResolvedAt(LocalDateTime.now());
        Dispute saved = disputeRepository.save(dispute);

        auditLogService.log(null, AuditActorType.ADMIN, FinancialAction.DISPUTE_RESOLVED,
                "Dispute", disputeId,
                String.format("Resolved: refund=%.2f, penalty=%.2f", refundAmount, providerPenalty));

        return saved;
    }

    @Transactional
    public Dispute reject(UUID disputeId, String notes) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));
        dispute.setStatus(DisputeStatus.REJECTED);
        dispute.setResolutionNotes(notes);
        dispute.setResolvedAt(LocalDateTime.now());
        Dispute saved = disputeRepository.save(dispute);

        auditLogService.log(null, AuditActorType.ADMIN, FinancialAction.DISPUTE_REJECTED,
                "Dispute", disputeId, "Dispute rejected: " + notes);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Dispute> getOpenDisputes() {
        return disputeRepository.findByStatus(DisputeStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByStatus(DisputeStatus status) {
        return disputeRepository.findByStatus(status);
    }
}
