package com.theguy.app.repository;

import com.theguy.app.entity.Payout;
import com.theguy.app.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    List<Payout> findByProviderIdOrderByCreatedAtDesc(UUID providerId);
    List<Payout> findByStatus(PayoutStatus status);
    long countByStatus(PayoutStatus status);
}
