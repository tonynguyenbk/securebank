package com.securebank.bankingcore.application.view;

import com.securebank.bankingcore.api.AdminLedgerEntryResponse;
import com.securebank.bankingcore.api.AdminTransactionDetailResponse;
import com.securebank.bankingcore.api.AdminTransactionResponse;
import com.securebank.bankingcore.api.LedgerLineResponse;
import com.securebank.bankingcore.api.StatementEntryResponse;
import com.securebank.bankingcore.api.TransactionDetailResponse;
import com.securebank.bankingcore.api.TransactionSummaryResponse;
import com.securebank.bankingcore.api.TransferResponse;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.EntryType;
import com.securebank.bankingcore.domain.LedgerEntry;
import com.securebank.common.web.Masking;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Entity → response mapping for transactions and ledger lines. Customer-facing views are computed relative
 * to the caller's own account IDs ({@code viewerAccountIds}): direction, counterparty, and the LedgerLine
 * privacy rule (another party's line shows a masked account number and no balances).
 */
@Component
public class TransactionViewMapper {

    public static final String DIRECTION_OUT = "OUT";
    public static final String DIRECTION_IN = "IN";

    /** DEBIT first, then CREDIT. */
    private static final Comparator<LedgerEntry> ENTRY_ORDER =
            Comparator.comparing((LedgerEntry e) -> e.getEntryType() == EntryType.DEBIT ? 0 : 1);

    public TransferResponse toTransferResponse(BankTransaction tx, Account source, Account destination,
                                               List<LedgerEntry> entries, Collection<UUID> viewerAccountIds) {
        return new TransferResponse(tx.getId(), tx.getTransactionReference(), tx.getStatus().name(),
                source.getAccountNumber(), destination.getAccountNumber(), destination.getCustomer().getFullName(),
                tx.getAmount(), tx.getCurrency(), tx.getDescription(), source.getBalance(), tx.getCreatedAt(),
                tx.getCompletedAt(), ledgerLines(entries, viewerAccountIds));
    }

    public TransactionSummaryResponse toSummary(BankTransaction tx, Collection<UUID> viewerAccountIds) {
        boolean outgoing = viewerAccountIds.contains(tx.getSourceAccount().getId());
        return new TransactionSummaryResponse(tx.getId(), tx.getTransactionReference(),
                outgoing ? DIRECTION_OUT : DIRECTION_IN, tx.getSourceAccount().getAccountNumber(),
                tx.getDestinationAccountNumber(), counterpartyName(tx, outgoing), tx.getAmount(), tx.getCurrency(),
                tx.getDescription(), tx.getStatus().name(), tx.getFailureCode(), tx.getCreatedAt());
    }

    public TransactionDetailResponse toDetail(BankTransaction tx, List<LedgerEntry> entries,
                                              Collection<UUID> viewerAccountIds) {
        boolean outgoing = viewerAccountIds.contains(tx.getSourceAccount().getId());
        return new TransactionDetailResponse(tx.getId(), tx.getTransactionReference(),
                outgoing ? DIRECTION_OUT : DIRECTION_IN, tx.getSourceAccount().getAccountNumber(),
                tx.getDestinationAccountNumber(), counterpartyName(tx, outgoing), tx.getAmount(), tx.getCurrency(),
                tx.getDescription(), tx.getStatus().name(), tx.getFailureCode(), tx.getCreatedAt(),
                tx.getFailureReason(), tx.getCompletedAt(), ledgerLines(entries, viewerAccountIds));
    }

    public List<LedgerLineResponse> ledgerLines(List<LedgerEntry> entries, Collection<UUID> viewerAccountIds) {
        return entries.stream().sorted(ENTRY_ORDER).map(e -> toLedgerLine(e, viewerAccountIds)).toList();
    }

    public LedgerLineResponse toLedgerLine(LedgerEntry entry, Collection<UUID> viewerAccountIds) {
        Account account = entry.getAccount();
        if (viewerAccountIds.contains(account.getId())) {
            return new LedgerLineResponse(entry.getEntryType().name(), account.getAccountNumber(), entry.getAmount(),
                    entry.getBalanceBefore(), entry.getBalanceAfter(), entry.getCreatedAt());
        }
        // someone else's account: never reveal their number or balances
        return new LedgerLineResponse(entry.getEntryType().name(), Masking.accountNumber(account.getAccountNumber()),
                entry.getAmount(), null, null, entry.getCreatedAt());
    }

    public StatementEntryResponse toStatementEntry(LedgerEntry entry) {
        BankTransaction tx = entry.getTransaction();
        boolean debit = entry.getEntryType() == EntryType.DEBIT;
        Account counterparty = debit ? tx.getDestinationAccount() : tx.getSourceAccount();
        String counterpartyNumber = debit ? tx.getDestinationAccountNumber() : tx.getSourceAccount().getAccountNumber();
        String counterpartyName = counterparty == null ? null : counterparty.getCustomer().getFullName();
        return new StatementEntryResponse(entry.getId(), tx.getId(), tx.getTransactionReference(),
                entry.getEntryType().name(), entry.getAmount(), entry.getBalanceBefore(), entry.getBalanceAfter(),
                counterpartyNumber, counterpartyName, tx.getDescription(), entry.getCreatedAt());
    }

    public AdminTransactionResponse toAdmin(BankTransaction tx) {
        Account source = tx.getSourceAccount();
        Account destination = tx.getDestinationAccount();
        return new AdminTransactionResponse(tx.getId(), tx.getTransactionReference(), source.getId(),
                source.getAccountNumber(), source.getCustomer().getFullName(),
                destination == null ? null : destination.getId(), tx.getDestinationAccountNumber(),
                destination == null ? null : destination.getCustomer().getFullName(), tx.getAmount(),
                tx.getCurrency(), tx.getDescription(), tx.getStatus().name(), tx.getFailureCode(),
                tx.getFailureReason(), tx.getCreatedBy(), tx.getCreatedAt(), tx.getCompletedAt());
    }

    public AdminTransactionDetailResponse toAdminDetail(BankTransaction tx, List<LedgerEntry> entries) {
        AdminTransactionResponse base = toAdmin(tx);
        return new AdminTransactionDetailResponse(base.id(), base.transactionReference(), base.sourceAccountId(),
                base.sourceAccountNumber(), base.sourceCustomerName(), base.destinationAccountId(),
                base.destinationAccountNumber(), base.destinationCustomerName(), base.amount(), base.currency(),
                base.description(), base.status(), base.failureCode(), base.failureReason(), base.createdBy(),
                base.createdAt(), base.completedAt(), adminLedger(entries));
    }

    public List<AdminLedgerEntryResponse> adminLedger(List<LedgerEntry> entries) {
        return entries.stream().sorted(ENTRY_ORDER).map(this::toAdminLedger).toList();
    }

    public AdminLedgerEntryResponse toAdminLedger(LedgerEntry entry) {
        return new AdminLedgerEntryResponse(entry.getId(), entry.getAccount().getId(),
                entry.getAccount().getAccountNumber(), entry.getEntryType().name(), entry.getAmount(),
                entry.getBalanceBefore(), entry.getBalanceAfter(), entry.getCreatedAt());
    }

    private static String counterpartyName(BankTransaction tx, boolean outgoing) {
        Account other = outgoing ? tx.getDestinationAccount() : tx.getSourceAccount();
        return other == null ? null : other.getCustomer().getFullName();
    }
}
