package com.theguy.app.repository;

import com.theguy.app.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByUserId(UUID userId);

    List<Provider> findByIsOnlineTrue();
}
