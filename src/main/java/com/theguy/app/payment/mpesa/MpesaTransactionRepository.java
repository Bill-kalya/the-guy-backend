package com.theguy.app.payment.mpesa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MpesaTransactionRepository extends JpaRepository<MpesaTransaction, UUID> {
    Optional<MpesaTransaction> findByCheckoutRequestId(String checkoutRequestId);
    Optional<MpesaTransaction> findByMpesaReceiptNumber(String mpesaReceiptNumber);
}
