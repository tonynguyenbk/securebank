package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.EntryType;
import com.securebank.bankingcore.domain.LedgerEntry;
import com.securebank.bankingcore.repository.LedgerEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Double-entry posting (spec §13): exactly one DEBIT on the source and one CREDIT on the destination, same
 * amount, each with the account's balance before and after. Insert-only; the database enforces uniqueness
 * per (transaction, entry type), arithmetic consistency and immutability.
 */
@Component
public class LedgerPoster {

    private final LedgerEntryRepository ledger;

    public LedgerPoster(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<LedgerEntry> post(BankTransaction transaction, Account source, Account.BalanceChange debit,
                                  Account destination, Account.BalanceChange credit, Instant now) {
        LedgerEntry debitEntry = ledger.save(new LedgerEntry(UUID.randomUUID(), transaction, source, EntryType.DEBIT,
                transaction.getAmount(), debit, now));
        LedgerEntry creditEntry = ledger.save(new LedgerEntry(UUID.randomUUID(), transaction, destination,
                EntryType.CREDIT, transaction.getAmount(), credit, now));
        return List.of(debitEntry, creditEntry);
    }
}
