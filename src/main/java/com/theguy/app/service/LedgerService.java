package com.theguy.app.service;

import com.theguy.app.entity.LedgerEntry;
import com.theguy.app.enums.AccountCode;
import com.theguy.app.enums.EntryType;
import com.theguy.app.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerEntry record(AccountCode account, EntryType entryType, double amount,
                              String currency, String referenceType, UUID referenceId, String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountCode(account)
                .entryType(entryType)
                .amount(amount)
                .currency(currency)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .build();

        LedgerEntry saved = ledgerEntryRepository.save(entry);
        log.info("Ledger entry: {} {} {} {} - {} [ref={}/{}]",
                entryType, amount, currency, account, description, referenceType, referenceId);
        return saved;
    }

    @Transactional
    public void recordDoubleEntry(AccountCode debitAccount, AccountCode creditAccount,
                                   double amount, String currency,
                                   String referenceType, UUID referenceId, String description) {
        record(debitAccount, EntryType.DEBIT, amount, currency, referenceType, referenceId, description);
        record(creditAccount, EntryType.CREDIT, amount, currency, referenceType, referenceId, description);
    }

    @Transactional(readOnly = true)
    public Double getAccountBalance(AccountCode account) {
        Double debits = ledgerEntryRepository.sumByAccountAndEntryType(account, EntryType.DEBIT);
        Double credits = ledgerEntryRepository.sumByAccountAndEntryType(account, EntryType.CREDIT);
        return (debits != null ? debits : 0.0) - (credits != null ? credits : 0.0);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForReference(String referenceType, UUID referenceId) {
        return ledgerEntryRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
    }
}
