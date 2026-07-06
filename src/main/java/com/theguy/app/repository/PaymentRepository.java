package com.theguy.app.repository;

import com.theguy.app.entity.Payment;
import com.theguy.app.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByCustomerId(UUID customerId);
    List<Payment> findByProviderId(UUID providerId);
    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);
    Optional<Payment> findByTransactionReference(String transactionReference);
    List<Payment> findByStatus(PaymentStatus status);
}