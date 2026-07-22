package com.theguy.app.repository;

import com.theguy.app.entity.LedgerEntry;
import com.theguy.app.enums.AccountCode;
import com.theguy.app.enums.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
    List<LedgerEntry> findByAccountCode(AccountCode accountCode);
    
    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.accountCode = :account AND le.entryType = :entryType")
    Double sumByAccountAndEntryType(@Param("account") AccountCode account, @Param("entryType") EntryType entryType);
}
