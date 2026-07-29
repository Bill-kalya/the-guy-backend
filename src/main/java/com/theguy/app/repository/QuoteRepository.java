package com.theguy.app.repository;

import com.theguy.app.entity.Quote;
import com.theguy.app.enums.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    List<Quote> findByJobIdOrderByCreatedAtDesc(UUID jobId);
    List<Quote> findByProviderIdOrderByCreatedAtDesc(UUID providerId);
    List<Quote> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    Optional<Quote> findFirstByJobIdAndProviderIdOrderByCreatedAtDesc(UUID jobId, UUID providerId);
    long countByProviderIdAndStatus(UUID providerId, QuoteStatus status);
    long countByCustomerIdAndStatus(UUID customerId, QuoteStatus status);
}
