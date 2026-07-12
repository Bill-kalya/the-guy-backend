package com.theguy.app.repository;

import com.theguy.app.entity.ModerationCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModerationCaseRepository extends JpaRepository<ModerationCase, UUID> {

    Page<ModerationCase> findByStatus(ModerationCase.ModerationStatus status, Pageable pageable);
}

