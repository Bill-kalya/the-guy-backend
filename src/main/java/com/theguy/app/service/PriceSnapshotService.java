package com.theguy.app.service;

import com.theguy.app.entity.BookingPriceSnapshot;
import com.theguy.app.entity.Job;
import com.theguy.app.repository.BookingPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSnapshotService {

    private final BookingPriceSnapshotRepository snapshotRepository;

    @Transactional
    public BookingPriceSnapshot capture(Job job, double servicePrice, double platformFee,
                                         double taxAmount, double discountAmount) {
        double totalAmount = servicePrice + platformFee + taxAmount - discountAmount;

        BookingPriceSnapshot snapshot = BookingPriceSnapshot.builder()
                .job(job)
                .serviceCategory(job.getServiceCategory())
                .servicePrice(servicePrice)
                .platformFee(platformFee)
                .taxAmount(taxAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .currency("KES")
                .build();

        BookingPriceSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Price snapshot captured: jobId={}, servicePrice={}, platformFee={}, tax={}, total={}",
                job.getId(), servicePrice, platformFee, taxAmount, totalAmount);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<BookingPriceSnapshot> getByJobId(UUID jobId) {
        return snapshotRepository.findByJobId(jobId);
    }
}
