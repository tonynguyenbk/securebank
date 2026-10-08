package com.securebank.bankingcore.controller;

import com.securebank.bankingcore.api.AccountAdminDetailResponse;
import com.securebank.bankingcore.api.AccountAdminResponse;
import com.securebank.bankingcore.api.AccountLimitsResponse;
import com.securebank.bankingcore.api.AccountStatusChangeRequest;
import com.securebank.bankingcore.api.AdminTransactionDetailResponse;
import com.securebank.bankingcore.api.AdminTransactionResponse;
import com.securebank.bankingcore.api.CustomerAdminDetailResponse;
import com.securebank.bankingcore.api.CustomerAdminResponse;
import com.securebank.bankingcore.api.DailyStatResponse;
import com.securebank.bankingcore.api.OpsStatsTodayResponse;
import com.securebank.bankingcore.api.ReconciliationResponse;
import com.securebank.bankingcore.api.UpdateTransferLimitsRequest;
import com.securebank.bankingcore.application.admin.AccountStatusService;
import com.securebank.bankingcore.application.admin.AdminQueryService;
import com.securebank.bankingcore.application.admin.LimitAdminService;
import com.securebank.bankingcore.application.admin.OpsStatsService;
import com.securebank.bankingcore.application.admin.ReconciliationService;
import com.securebank.bankingcore.application.query.TransactionFilter;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.security.Roles;
import com.securebank.common.error.ApiError;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bank operations endpoints (contract §3). Reads: BANK_STAFF, AUDITOR, ADMIN. Mutations: BANK_STAFF, ADMIN
 * (AUDITOR never mutates). Reconciliation: AUDITOR, ADMIN. CUSTOMER gets 403 FORBIDDEN_OPERATION everywhere.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Bank operations", description = "Staff / auditor / admin endpoints")
@ApiResponse(responseCode = "401", description = "Missing/invalid token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "FORBIDDEN_OPERATION (role not allowed)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class AdminController {

    private final AdminQueryService queries;
    private final AccountStatusService statuses;
    private final LimitAdminService limits;
    private final ReconciliationService reconciliation;
    private final OpsStatsService stats;

    public AdminController(AdminQueryService queries, AccountStatusService statuses, LimitAdminService limits,
                           ReconciliationService reconciliation, OpsStatsService stats) {
        this.queries = queries;
        this.statuses = statuses;
        this.limits = limits;
        this.reconciliation = reconciliation;
        this.stats = stats;
    }

    @GetMapping("/customers")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Search customers", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. q matches name/email/phone.")
    public PageResponse<CustomerAdminResponse> customers(@RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return queries.customers(q, page, size);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Customer with accounts", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. 404 CUSTOMER_NOT_FOUND.")
    public CustomerAdminDetailResponse customer(@PathVariable UUID id) {
        return queries.customer(id);
    }

    @GetMapping("/accounts")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Search accounts", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. q matches account number "
            + "or customer name.")
    public PageResponse<AccountAdminResponse> accounts(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) AccountStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return queries.accounts(q, status, page, size);
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Account with limits", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. 404 ACCOUNT_NOT_FOUND.")
    public AccountAdminDetailResponse account(@PathVariable UUID id) {
        return queries.account(id);
    }

    @PatchMapping("/accounts/{id}/freeze")
    @PreAuthorize(Roles.STAFF_ADMIN)
    @Operation(summary = "Freeze an account",
            description = "Roles: BANK_STAFF, ADMIN. The account can no longer send money (it can still receive). "
                    + "Audited (ACCOUNT_FREEZE) and published as AccountStatusChangedEvent.")
    @ApiResponse(responseCode = "409", description = "ACCOUNT_STATUS_UNCHANGED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "ACCOUNT_CLOSED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AccountAdminResponse freeze(@PathVariable UUID id, @Valid @RequestBody AccountStatusChangeRequest request) {
        return statuses.freeze(CurrentUser.require(), id, request.reason());
    }

    @PatchMapping("/accounts/{id}/unfreeze")
    @PreAuthorize(Roles.STAFF_ADMIN)
    @Operation(summary = "Unfreeze an account", description = "Roles: BANK_STAFF, ADMIN. Audited (ACCOUNT_UNFREEZE).")
    @ApiResponse(responseCode = "409", description = "ACCOUNT_STATUS_UNCHANGED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AccountAdminResponse unfreeze(@PathVariable UUID id,
                                         @Valid @RequestBody AccountStatusChangeRequest request) {
        return statuses.unfreeze(CurrentUser.require(), id, request.reason());
    }

    @GetMapping("/accounts/{id}/limits")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Transfer limits and today's usage", description = "Roles: BANK_STAFF, AUDITOR, ADMIN.")
    public AccountLimitsResponse getLimits(@PathVariable UUID id) {
        return queries.limits(id);
    }

    @PutMapping("/accounts/{id}/limits")
    @PreAuthorize(Roles.STAFF_ADMIN)
    @Operation(summary = "Update transfer limits",
            description = "Roles: BANK_STAFF, ADMIN. Both > 0, per-transaction <= daily. Audited "
                    + "(TRANSFER_LIMIT_UPDATE with before/after).")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AccountLimitsResponse updateLimits(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateTransferLimitsRequest request) {
        return limits.update(CurrentUser.require(), id, request);
    }

    @GetMapping("/transactions")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Search all transactions", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. accountNumber "
            + "matches source or destination; dates inclusive (Asia/Ho_Chi_Minh).")
    public PageResponse<AdminTransactionResponse> transactions(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var filter = new TransactionFilter(null, accountNumber, reference, status, fromDate, toDate, minAmount,
                maxAmount);
        return queries.transactions(filter, page, size, sort);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Transaction with full ledger", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. 404 "
            + "TRANSACTION_NOT_FOUND.")
    public AdminTransactionDetailResponse transaction(@PathVariable UUID id) {
        return queries.transaction(id);
    }

    @GetMapping("/reconciliation/transactions/{id}")
    @PreAuthorize(Roles.AUDITOR_ADMIN)
    @Operation(summary = "Reconcile a transaction's ledger",
            description = "Roles: AUDITOR, ADMIN. balanced = debits == credits and 2 entries for SUCCESS (0 otherwise).")
    public ReconciliationResponse reconcile(@PathVariable UUID id) {
        return reconciliation.reconcile(id);
    }

    @GetMapping("/stats/today")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "Today's operations numbers", description = "Roles: BANK_STAFF, AUDITOR, ADMIN.")
    public OpsStatsTodayResponse statsToday() {
        return stats.today();
    }

    @GetMapping("/stats/daily")
    @PreAuthorize(Roles.STAFF_AUDITOR_ADMIN)
    @Operation(summary = "SUCCESS volume per day, oldest first", description = "Roles: BANK_STAFF, AUDITOR, ADMIN. "
            + "days 1-90 (default 14).")
    public List<DailyStatResponse> statsDaily(@RequestParam(defaultValue = "14") int days) {
        return stats.daily(days);
    }
}
