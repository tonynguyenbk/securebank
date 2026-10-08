package com.securebank.bankingcore.application.admin;

import com.securebank.bankingcore.api.ReconciliationResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.view.TransactionViewMapper;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.EntryType;
import com.securebank.bankingcore.domain.LedgerEntry;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.repository.LedgerEntryRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ledger reconciliation of one transaction (spec §13). {@code balanced} is true when debits equal credits and
 * the entry count is 2 for SUCCESS (0 otherwise). For SUCCESS it additionally requires one DEBIT on the source,
 * one CREDIT on the destination, both equal to the transaction amount, with consistent running balances —
 * a stricter reading of "balanced" that would also catch a corrupted (but self-balancing) pair.
 */
@Service
@Transactional(readOnly = true)
public class ReconciliationService {

    private final BankTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final TransactionViewMapper views;
    private final BusinessCalendar calendar;

    public ReconciliationService(BankTransactionRepository transactions, LedgerEntryRepository ledger,
                                 TransactionViewMapper views, BusinessCalendar calendar) {
        this.transactions = transactions;
        this.ledger = ledger;
        this.views = views;
        this.calendar = calendar;
    }

    public ReconciliationResponse reconcile(UUID transactionId) {
        BankTransaction tx = transactions.findDetailedById(transactionId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        List<LedgerEntry> entries = ledger.findByTransactionId(transactionId);
        BigDecimal debits = total(entries, EntryType.DEBIT);
        BigDecimal credits = total(entries, EntryType.CREDIT);
        boolean balanced = debits.compareTo(credits) == 0 && structurallyValid(tx, entries);
        return new ReconciliationResponse(tx.getId(), tx.getTransactionReference(), tx.getStatus().name(),
                entries.size(), debits, credits, balanced, views.adminLedger(entries), calendar.now());
    }

    static boolean structurallyValid(BankTransaction tx, List<LedgerEntry> entries) {
        if (tx.getStatus() != TransactionStatus.SUCCESS) {
            return entries.isEmpty();
        }
        if (entries.size() != 2) {
            return false;
        }
        for (LedgerEntry e : entries) {
            boolean debit = e.getEntryType() == EntryType.DEBIT;
            UUID expectedAccount = debit ? tx.getSourceAccount().getId()
                    : tx.getDestinationAccount() == null ? null : tx.getDestinationAccount().getId();
            BigDecimal expectedAfter = debit ? e.getBalanceBefore().subtract(e.getAmount())
                    : e.getBalanceBefore().add(e.getAmount());
            if (!e.getAccount().getId().equals(expectedAccount)
                    || e.getAmount().compareTo(tx.getAmount()) != 0
                    || e.getBalanceAfter().compareTo(expectedAfter) != 0) {
                return false;
            }
        }
        return entries.stream().map(LedgerEntry::getEntryType).distinct().count() == 2;
    }

    private static BigDecimal total(List<LedgerEntry> entries, EntryType type) {
        return entries.stream().filter(e -> e.getEntryType() == type).map(LedgerEntry::getAmount)
                .reduce(Money.zero(), BigDecimal::add);
    }
}
