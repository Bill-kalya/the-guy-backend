package com.theguy.app.repository;

import com.theguy.app.entity.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Page<Dispute> findByStatus(Dispute.DisputeStatus status, Pageable pageable);

    @Query("SELECT COALESCE(COUNT(d),0) FROM Dispute d WHERE d.status = 'OPEN'")
    long countByOpenStatus();

    long countByStatus(Dispute.DisputeStatus status);
}

