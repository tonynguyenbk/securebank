package com.securebank.bankingcore.application.view;

import com.securebank.bankingcore.api.AccountAdminDetailResponse;
import com.securebank.bankingcore.api.AccountAdminResponse;
import com.securebank.bankingcore.api.AccountBalanceResponse;
import com.securebank.bankingcore.api.AccountDetailResponse;
import com.securebank.bankingcore.api.AccountLimitsResponse;
import com.securebank.bankingcore.api.AccountSummaryResponse;
import com.securebank.bankingcore.api.CustomerAdminDetailResponse;
import com.securebank.bankingcore.api.CustomerAdminResponse;
import com.securebank.bankingcore.api.CustomerResponse;
import com.securebank.bankingcore.api.TransferLimitsResponse;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Customer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entity → response mapping for customers, accounts and limits. Entities never leave the service layer. */
@Component
public class AccountViewMapper {

    public CustomerResponse toCustomer(Customer c) {
        return new CustomerResponse(c.getId(), c.getUserId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getCreatedAt());
    }

    public CustomerAdminResponse toCustomerAdmin(Customer c, long accountCount) {
        return new CustomerAdminResponse(c.getId(), c.getUserId(), c.getFullName(), c.getEmail(), c.getPhone(),
                accountCount, c.getCreatedAt());
    }

    public CustomerAdminDetailResponse toCustomerAdminDetail(Customer c, List<AccountAdminResponse> accounts) {
        return new CustomerAdminDetailResponse(c.getId(), c.getUserId(), c.getFullName(), c.getEmail(), c.getPhone(),
                accounts.size(), c.getCreatedAt(), accounts);
    }

    public AccountSummaryResponse toSummary(Account a) {
        return new AccountSummaryResponse(a.getId(), a.getAccountNumber(), a.getAccountType(), a.getCurrency(),
                a.getBalance(), a.getStatus().name(), a.getCreatedAt());
    }

    public AccountDetailResponse toDetail(Account a, TransferLimitsResponse limits) {
        return new AccountDetailResponse(a.getId(), a.getAccountNumber(), a.getAccountType(), a.getCurrency(),
                a.getBalance(), a.getStatus().name(), a.getCreatedAt(), a.getCustomer().getId(), limits,
                a.getUpdatedAt());
    }

    public AccountBalanceResponse toBalance(Account a, Instant asOf) {
        return new AccountBalanceResponse(a.getId(), a.getAccountNumber(), a.getBalance(), a.getCurrency(),
                a.getStatus().name(), asOf);
    }

    public AccountAdminResponse toAdmin(Account a) {
        return new AccountAdminResponse(a.getId(), a.getAccountNumber(), a.getCustomer().getId(),
                a.getCustomer().getFullName(), a.getCurrency(), a.getBalance(), a.getStatus().name(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    public AccountAdminDetailResponse toAdminDetail(Account a, TransferLimitsResponse limits) {
        return new AccountAdminDetailResponse(a.getId(), a.getAccountNumber(), a.getCustomer().getId(),
                a.getCustomer().getFullName(), a.getCurrency(), a.getBalance(), a.getStatus().name(),
                a.getCreatedAt(), a.getUpdatedAt(), limits);
    }

    public AccountLimitsResponse toLimits(UUID accountId, TransferLimitsResponse limits) {
        return new AccountLimitsResponse(accountId, limits.perTransactionLimit(), limits.dailyLimit(),
                limits.usedToday(), limits.remainingToday(), limits.updatedAt());
    }
}
