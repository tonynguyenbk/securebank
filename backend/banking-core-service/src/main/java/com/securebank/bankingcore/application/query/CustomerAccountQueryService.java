package com.securebank.bankingcore.application.query;

import com.securebank.bankingcore.api.AccountBalanceResponse;
import com.securebank.bankingcore.api.AccountDetailResponse;
import com.securebank.bankingcore.api.AccountLookupResponse;
import com.securebank.bankingcore.api.AccountSummaryResponse;
import com.securebank.bankingcore.api.CustomerResponse;
import com.securebank.bankingcore.api.StatementEntryResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.application.view.AccountViewMapper;
import com.securebank.bankingcore.application.view.TransactionViewMapper;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.CustomerRepository;
import com.securebank.bankingcore.repository.LedgerEntryRepository;
import com.securebank.bankingcore.security.OwnershipGuard;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Customer read endpoints: own profile, own accounts, balance, statement, beneficiary lookup. */
@Service
@Transactional(readOnly = true)
public class CustomerAccountQueryService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferLimitService limits;
    private final OwnershipGuard ownership;
    private final AccountViewMapper accountViews;
    private final TransactionViewMapper transactionViews;
    private final BusinessCalendar calendar;

    public CustomerAccountQueryService(CustomerRepository customers, AccountRepository accounts,
                                       LedgerEntryRepository ledger, TransferLimitService limits,
                                       OwnershipGuard ownership, AccountViewMapper accountViews,
                                       TransactionViewMapper transactionViews, BusinessCalendar calendar) {
        this.customers = customers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.limits = limits;
        this.ownership = ownership;
        this.accountViews = accountViews;
        this.transactionViews = transactionViews;
        this.calendar = calendar;
    }

    public CustomerResponse me(AuthenticatedUser user) {
        return customers.findByUserId(user.userId()).map(accountViews::toCustomer)
                .orElseThrow(() -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    public List<AccountSummaryResponse> myAccounts(AuthenticatedUser user) {
        return accounts.findByOwnerUserId(user.userId()).stream().map(accountViews::toSummary).toList();
    }

    public AccountDetailResponse accountDetail(AuthenticatedUser user, UUID accountId) {
        Account account = ownedAccount(user, accountId);
        return accountViews.toDetail(account, limits.usage(account.getId()));
    }

    public AccountBalanceResponse balance(AuthenticatedUser user, UUID accountId) {
        return accountViews.toBalance(ownedAccount(user, accountId), calendar.now());
    }

    public PageResponse<StatementEntryResponse> statement(AuthenticatedUser user, UUID accountId, LocalDate fromDate,
                                                          LocalDate toDate, int page, int size) {
        Account account = ownedAccount(user, accountId);
        BusinessCalendar.Range range = calendar.between(fromDate, toDate);
        return PageResponse.of(ledger.findStatement(account.getId(), range.from(), range.to(),
                Paging.unsorted(page, size)), transactionViews::toStatementEntry);
    }

    /** Beneficiary name check. CLOSED accounts are reported as not found (they cannot receive money). */
    public AccountLookupResponse lookup(String accountNumber) {
        String number = accountNumber == null ? "" : accountNumber.trim();
        if (!number.matches("\\d{10}")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "accountNumber must be a 10-digit account number");
        }
        return accounts.findWithCustomerByAccountNumber(number)
                .filter(a -> a.getStatus() != AccountStatus.CLOSED)
                .map(a -> new AccountLookupResponse(a.getAccountNumber(), a.getCustomer().getFullName(),
                        a.getCurrency()))
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** 404 if the account does not exist, 403 ACCOUNT_NOT_OWNED if it belongs to someone else (contract §2). */
    Account ownedAccount(AuthenticatedUser user, UUID accountId) {
        Account account = accounts.findWithCustomerById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        ownership.requireOwner(user, account);
        return account;
    }
}
