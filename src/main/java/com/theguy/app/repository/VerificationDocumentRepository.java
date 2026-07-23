package com.theguy.app.repository;

import com.theguy.app.entity.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findByProviderIdAndStatusNotOrderByCreatedAtDesc(UUID providerId, VerificationDocument.VerificationDocumentStatus status);

    Optional<VerificationDocument> findByPublicId(String publicId);

    @Query("SELECT vd FROM VerificationDocument vd WHERE vd.publicId = :publicId AND vd.provider.user.id = :userId")
    Optional<VerificationDocument> findByPublicIdAndUserId(@Param("publicId") String publicId, @Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE VerificationDocument vd SET vd.status = 'DELETED' WHERE vd.publicId = :publicId")
    void softDeleteByPublicId(@Param("publicId") String publicId);

    long countByProviderIdAndStatus(UUID providerId, VerificationDocument.VerificationDocumentStatus status);
}
