package com.securebank.bankingcore.application.query;

import com.securebank.bankingcore.api.TransactionDetailResponse;
import com.securebank.bankingcore.api.TransactionSummaryResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.view.TransactionViewMapper;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.repository.LedgerEntryRepository;
import com.securebank.bankingcore.security.OwnershipGuard;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Customer transaction history (GET /transfers) and detail (GET /transfers/{id}). */
@Service
@Transactional(readOnly = true)
public class CustomerTransactionQueryService {

    static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "transactionReference");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final OwnershipGuard ownership;
    private final TransactionViewMapper views;
    private final BusinessCalendar calendar;

    public CustomerTransactionQueryService(AccountRepository accounts, BankTransactionRepository transactions,
                                           LedgerEntryRepository ledger, OwnershipGuard ownership,
                                           TransactionViewMapper views, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.ownership = ownership;
        this.views = views;
        this.calendar = calendar;
    }

    public PageResponse<TransactionSummaryResponse> list(AuthenticatedUser user, TransactionFilter filter, int page,
                                                         int size, String sort) {
        List<UUID> myAccounts = accounts.findIdsByOwnerUserId(user.userId());
        if (filter.accountId() != null) {
            accounts.findWithCustomerById(filter.accountId())
                    .ifPresentOrElse(a -> ownership.requireOwner(user, a),
                            () -> {
                                throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND);
                            });
        }
        var pageable = Paging.of(page, size, sort, SORTABLE, DEFAULT_SORT);
        if (myAccounts.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        var spec = TransactionSpecifications.visibleTo(myAccounts)
                .and(TransactionSpecifications.matching(filter, calendar));
        return PageResponse.of(transactions.findAll(spec, pageable), tx -> views.toSummary(tx, myAccounts));
    }

    /** 404 TRANSACTION_NOT_FOUND both when it does not exist and when the caller is not a party. */
    public TransactionDetailResponse detail(AuthenticatedUser user, UUID transactionId) {
        List<UUID> myAccounts = accounts.findIdsByOwnerUserId(user.userId());
        BankTransaction tx = transactions.findDetailedById(transactionId)
                .filter(t -> ownership.canViewTransaction(myAccounts, t.getSourceAccount().getId(),
                        t.getDestinationAccount() == null ? null : t.getDestinationAccount().getId(),
                        t.getStatus() == TransactionStatus.REJECTED))
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        return views.toDetail(tx, ledger.findByTransactionId(tx.getId()), myAccounts);
    }
}
