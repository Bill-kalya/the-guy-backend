package com.theguy.app.service;

import com.theguy.app.entity.Job;
import com.theguy.app.entity.Quote;
import com.theguy.app.enums.JobStatus;
import com.theguy.app.enums.QuoteStatus;
import com.theguy.app.repository.JobRepository;
import com.theguy.app.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final JobRepository jobRepository;

    @Transactional
    public Quote submitQuote(UUID jobId, UUID providerId, BigDecimal amount,
                             String description, Integer estimatedDurationMinutes) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        Quote quote = Quote.builder()
                .jobId(jobId)
                .providerId(providerId)
                .customerId(job.getCustomer().getId())
                .amount(amount)
                .description(description)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .status(QuoteStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        Quote saved = quoteRepository.save(quote);
        log.info("Quote submitted: provider={}, job={}, amount={}", providerId, jobId, amount);
        return saved;
    }

    @Transactional
    public Quote acceptQuote(UUID quoteId, UUID customerId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found: " + quoteId));

        if (!quote.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("Quote does not belong to this customer");
        }
        if (quote.getStatus() != QuoteStatus.PENDING && quote.getStatus() != QuoteStatus.COUNTERED) {
            throw new IllegalStateException("Quote is not in a respondable state: " + quote.getStatus());
        }

        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setRespondedAt(LocalDateTime.now());

        Job job = jobRepository.findById(quote.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + quote.getJobId()));
        job.setFinalPrice(quote.getAmount().doubleValue());
        job.setStatus(JobStatus.ASSIGNED);
        job.setAcceptedAt(LocalDateTime.now());
        jobRepository.save(job);

        Quote saved = quoteRepository.save(quote);
        log.info("Quote accepted: quoteId={}, jobId={}, price locked at={}", quoteId, quote.getJobId(), quote.getAmount());
        return saved;
    }

    @Transactional
    public Quote rejectQuote(UUID quoteId, UUID customerId, String reason) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found: " + quoteId));

        if (!quote.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("Quote does not belong to this customer");
        }

        quote.setStatus(QuoteStatus.REJECTED);
        quote.setRejectionReason(reason);
        quote.setRespondedAt(LocalDateTime.now());

        Quote saved = quoteRepository.save(quote);
        log.info("Quote rejected: quoteId={}, reason={}", quoteId, reason);
        return saved;
    }

    @Transactional
    public Quote counterOffer(UUID quoteId, UUID customerId, BigDecimal counterAmount) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found: " + quoteId));

        if (!quote.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("Quote does not belong to this customer");
        }
        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new IllegalStateException("Can only counter a pending quote");
        }

        quote.setStatus(QuoteStatus.COUNTERED);
        quote.setCounterAmount(counterAmount);
        quote.setRespondedAt(LocalDateTime.now());

        Quote saved = quoteRepository.save(quote);
        log.info("Counter-offer submitted: quoteId={}, amount={}", quoteId, counterAmount);
        return saved;
    }

    @Transactional
    public Quote acceptCounterOffer(UUID quoteId, UUID providerId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found: " + quoteId));

        if (!quote.getProviderId().equals(providerId)) {
            throw new IllegalStateException("Quote does not belong to this provider");
        }
        if (quote.getStatus() != QuoteStatus.COUNTERED) {
            throw new IllegalStateException("No counter-offer to accept");
        }

        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAmount(quote.getCounterAmount());
        quote.setCounterAmount(null);
        quote.setRespondedAt(LocalDateTime.now());

        Job job = jobRepository.findById(quote.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + quote.getJobId()));
        job.setFinalPrice(quote.getAmount().doubleValue());
        job.setStatus(JobStatus.ASSIGNED);
        job.setAcceptedAt(LocalDateTime.now());
        jobRepository.save(job);

        Quote saved = quoteRepository.save(quote);
        log.info("Counter-offer accepted by provider: quoteId={}, finalPrice={}", quoteId, quote.getAmount());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Quote> getQuotesForJob(UUID jobId) {
        return quoteRepository.findByJobIdOrderByCreatedAtDesc(jobId);
    }

    @Transactional(readOnly = true)
    public List<Quote> getProviderQuotes(UUID providerId) {
        return quoteRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    @Transactional(readOnly = true)
    public List<Quote> getCustomerQuotes(UUID customerId) {
        return quoteRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
