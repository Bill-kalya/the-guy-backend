package com.theguy.app.repository;

import com.theguy.app.entity.BookingPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPriceSnapshotRepository extends JpaRepository<BookingPriceSnapshot, UUID> {
    Optional<BookingPriceSnapshot> findByJobId(UUID jobId);
}
