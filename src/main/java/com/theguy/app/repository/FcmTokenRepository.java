package com.theguy.app.repository;

import com.theguy.app.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {
    List<FcmToken> findAllByUserId(UUID userId);
    void deleteByToken(String token);
}
