package com.securebank.bankingcore.controller;

import com.securebank.bankingcore.api.CreateTransferRequest;
import com.securebank.bankingcore.api.TransactionDetailResponse;
import com.securebank.bankingcore.api.TransactionSummaryResponse;
import com.securebank.bankingcore.api.TransferResponse;
import com.securebank.bankingcore.application.query.CustomerTransactionQueryService;
import com.securebank.bankingcore.application.query.TransactionFilter;
import com.securebank.bankingcore.application.transfer.TransferResult;
import com.securebank.bankingcore.application.transfer.TransferService;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.security.Roles;
import com.securebank.common.error.ApiError;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Internal transfers and transaction history (role CUSTOMER)")
@ApiResponse(responseCode = "401", description = "Missing/invalid token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class TransferController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransferService transfers;
    private final CustomerTransactionQueryService queries;

    public TransferController(TransferService transfers, CustomerTransactionQueryService queries) {
        this.transfers = transfers;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Transfer money from an own account",
            description = """
                    Role: CUSTOMER (owner of the source account). Atomic: debit, credit, two ledger entries, \
                    transaction record, outbox event and idempotency record commit together or not at all.
                    Retrying with the same Idempotency-Key and payload returns the stored status and body with \
                    header Idempotent-Replayed: true and never moves money again. A frozen destination still \
                    receives money; a frozen source cannot send.""")
    @Parameter(name = IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true,
            description = "Client-generated unique key per transfer (8-100 chars [A-Za-z0-9_-], e.g. a UUID). "
                    + "Scope: (user, key).",
            example = "5f0c7f8e-2b5d-4a1e-9c3f-7d1b2a6e4c10")
    @ApiResponse(responseCode = "201", description = "Transfer completed (or replayed)",
            headers = @Header(name = IDEMPOTENT_REPLAYED, description = "true when this is a stored response"),
            content = @Content(schema = @Schema(implementation = TransferResponse.class)))
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED, VALIDATION_FAILED, "
            + "INVALID_TRANSFER_AMOUNT, CURRENCY_NOT_SUPPORTED, SAME_ACCOUNT_TRANSFER",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "ACCOUNT_NOT_OWNED, FORBIDDEN_OPERATION",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND (source or destination)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_CONFLICT (same key, different payload), "
            + "IDEMPOTENCY_REQUEST_IN_PROGRESS",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Rejected and recorded as REJECTED: ACCOUNT_FROZEN, "
            + "ACCOUNT_CLOSED, CURRENCY_MISMATCH, TRANSFER_LIMIT_EXCEEDED, DAILY_LIMIT_EXCEEDED, INSUFFICIENT_FUNDS",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "ACCOUNT_BUSY: account locked by another transfer (lock timeout), retry with the same key",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<String> create(@Parameter(hidden = true) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                         @Valid @RequestBody CreateTransferRequest request) {
        TransferResult result = transfers.submit(CurrentUser.require(), idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        // the exact stored JSON, so a replay is byte-for-byte the original response
        return response.body(result.body());
    }

    @GetMapping
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Own transaction history",
            description = "Role: CUSTOMER. Transactions where an own account is source or destination; REJECTED "
                    + "ones only as sender. Dates inclusive (Asia/Ho_Chi_Minh). sort: createdAt|amount|status|"
                    + "transactionReference, default createdAt,desc.")
    public PageResponse<TransactionSummaryResponse> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var filter = new TransactionFilter(accountId, null, null, status, fromDate, toDate, minAmount, maxAmount);
        return queries.list(CurrentUser.require(), filter, page, size, sort);
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize(Roles.CUSTOMER)
    @Operation(summary = "Transaction detail with ledger lines",
            description = "Role: CUSTOMER (party). 404 TRANSACTION_NOT_FOUND also when the caller is not a party. "
                    + "The counterparty's ledger line shows a masked account number and no balances.")
    public TransactionDetailResponse detail(@PathVariable UUID transactionId) {
        return queries.detail(CurrentUser.require(), transactionId);
    }
}
