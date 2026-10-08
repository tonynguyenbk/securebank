package com.securebank.bankingcore.application.admin;

import com.securebank.bankingcore.api.AccountAdminDetailResponse;
import com.securebank.bankingcore.api.AccountAdminResponse;
import com.securebank.bankingcore.api.AccountLimitsResponse;
import com.securebank.bankingcore.api.AdminTransactionDetailResponse;
import com.securebank.bankingcore.api.AdminTransactionResponse;
import com.securebank.bankingcore.api.CustomerAdminDetailResponse;
import com.securebank.bankingcore.api.CustomerAdminResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.application.query.AdminTransactionQueries;
import com.securebank.bankingcore.application.query.Paging;
import com.securebank.bankingcore.application.query.TransactionFilter;
import com.securebank.bankingcore.application.view.AccountViewMapper;
import com.securebank.bankingcore.application.view.TransactionViewMapper;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.repository.CustomerRepository;
import com.securebank.bankingcore.repository.LedgerEntryRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only staff/auditor views: customers, accounts, limits, transactions (contract §3). */
@Service
@Transactional(readOnly = true)
public class AdminQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
    private static final Set<String> TRANSACTION_SORTABLE =
            Set.of("createdAt", "amount", "status", "transactionReference");

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final TransferLimitService limits;
    private final AccountViewMapper accountViews;
    private final TransactionViewMapper transactionViews;
    private final BusinessCalendar calendar;

    public AdminQueryService(CustomerRepository customers, AccountRepository accounts,
                             BankTransactionRepository transactions, LedgerEntryRepository ledger,
                             TransferLimitService limits, AccountViewMapper accountViews,
                             TransactionViewMapper transactionViews, BusinessCalendar calendar) {
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.limits = limits;
        this.accountViews = accountViews;
        this.transactionViews = transactionViews;
        this.calendar = calendar;
    }

    public PageResponse<CustomerAdminResponse> customers(String q, int page, int size) {
        var pageable = Paging.of(page, size, null, Set.of(), NEWEST_FIRST);
        return PageResponse.of(customers.search(Paging.containsPattern(q), pageable),
                row -> accountViews.toCustomerAdmin(row.customer(), row.accountCount()));
    }

    public CustomerAdminDetailResponse customer(UUID id) {
        Customer customer = customers.findById(id).orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));
        List<AccountAdminResponse> owned = accounts.findByCustomerId(id).stream()
                .map(a -> accountViews.toAdmin(a)).toList();
        return accountViews.toCustomerAdminDetail(customer, owned);
    }

    public PageResponse<AccountAdminResponse> accounts(String q, AccountStatus status, int page, int size) {
        var pageable = Paging.of(page, size, null, Set.of(), NEWEST_FIRST);
        return PageResponse.of(accounts.findAll(accountSearch(q, status), pageable), accountViews::toAdmin);
    }

    public AccountAdminDetailResponse account(UUID id) {
        Account account = requireAccount(id);
        return accountViews.toAdminDetail(account, limits.usage(account.getId()));
    }

    public AccountLimitsResponse limits(UUID accountId) {
        Account account = requireAccount(accountId);
        return accountViews.toLimits(account.getId(), limits.usage(account.getId()));
    }

    public PageResponse<AdminTransactionResponse> transactions(TransactionFilter filter, int page, int size,
                                                               String sort) {
        var pageable = Paging.of(page, size, sort, TRANSACTION_SORTABLE, NEWEST_FIRST);
        return PageResponse.of(transactions.findAll(AdminTransactionQueries.matching(filter, calendar), pageable),
                transactionViews::toAdmin);
    }

    public AdminTransactionDetailResponse transaction(UUID id) {
        BankTransaction tx = transactions.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        return transactionViews.toAdminDetail(tx, ledger.findByTransactionId(id));
    }

    private Account requireAccount(UUID id) {
        return accounts.findWithCustomerById(id).orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private static Specification<Account> accountSearch(String q, AccountStatus status) {
        String pattern = Paging.containsPattern(q);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!"%".equals(pattern)) {
                var customer = root.join("customer");
                predicates.add(cb.or(
                        cb.like(root.get("accountNumber"), pattern, '!'),
                        cb.like(cb.lower(customer.get("fullName")), pattern, '!')));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
