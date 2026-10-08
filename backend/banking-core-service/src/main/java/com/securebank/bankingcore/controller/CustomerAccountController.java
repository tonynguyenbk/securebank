package com.securebank.bankingcore.controller;

import com.securebank.bankingcore.api.AccountBalanceResponse;
import com.securebank.bankingcore.api.AccountDetailResponse;
import com.securebank.bankingcore.api.AccountLookupResponse;
import com.securebank.bankingcore.api.AccountSummaryResponse;
import com.securebank.bankingcore.api.CustomerResponse;
import com.securebank.bankingcore.api.StatementEntryResponse;
import com.securebank.bankingcore.application.query.CustomerAccountQueryService;
import com.securebank.bankingcore.security.Roles;
import com.securebank.common.error.ApiError;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Customer accounts", description = "The signed-in customer's profile and accounts (role CUSTOMER)")
@ApiResponse(responseCode = "401", description = "Missing/invalid token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "FORBIDDEN_OPERATION (wrong role) or ACCOUNT_NOT_OWNED",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class CustomerAccountController {

    private final CustomerAccountQueryService queries;

    public CustomerAccountController(CustomerAccountQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/customers/me")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Own customer profile", description = "Role: CUSTOMER. 404 CUSTOMER_NOT_FOUND if no profile.")
    public CustomerResponse me() {
        return queries.me(CurrentUser.require());
    }

    @GetMapping("/accounts")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Own accounts", description = "Role: CUSTOMER.")
    public List<AccountSummaryResponse> accounts() {
        return queries.myAccounts(CurrentUser.require());
    }

    @GetMapping("/accounts/lookup")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Beneficiary lookup",
            description = "Role: CUSTOMER. Returns the holder name for a 10-digit account number before sending. "
                    + "404 ACCOUNT_NOT_FOUND also for CLOSED accounts.")
    public AccountLookupResponse lookup(@RequestParam String accountNumber) {
        return queries.lookup(accountNumber);
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Own account detail with limits usage",
            description = "Role: CUSTOMER (owner). 404 ACCOUNT_NOT_FOUND, 403 ACCOUNT_NOT_OWNED.")
    public AccountDetailResponse account(@PathVariable UUID accountId) {
        return queries.accountDetail(CurrentUser.require(), accountId);
    }

    @GetMapping("/accounts/{accountId}/balance")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Current balance", description = "Role: CUSTOMER (owner). 404, 403.")
    public AccountBalanceResponse balance(@PathVariable UUID accountId) {
        return queries.balance(CurrentUser.require(), accountId);
    }

    @GetMapping("/accounts/{accountId}/statement")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Account statement (ledger lines), newest first",
            description = "Role: CUSTOMER (owner). fromDate/toDate are inclusive YYYY-MM-DD in Asia/Ho_Chi_Minh.")
    public PageResponse<StatementEntryResponse> statement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.statement(CurrentUser.require(), accountId, fromDate, toDate, page, size);
    }
}
