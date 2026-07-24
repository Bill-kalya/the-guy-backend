package com.theguy.app.repository;

import com.theguy.app.entity.ProviderReputation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderReputationRepository extends JpaRepository<ProviderReputation, UUID> {
}
