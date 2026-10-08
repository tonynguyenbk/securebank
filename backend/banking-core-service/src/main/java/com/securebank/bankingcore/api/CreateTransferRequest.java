package com.securebank.bankingcore.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /transfers. Only shape is validated here (400 VALIDATION_FAILED); money rules (amount &gt; 0,
 * scale &le; 2 → INVALID_TRANSFER_AMOUNT, VND only → CURRENCY_NOT_SUPPORTED, different accounts →
 * SAME_ACCOUNT_TRANSFER) are applied by {@code TransferRequestValidator} so they get their own error codes.
 */
public record CreateTransferRequest(
        @Schema(description = "Own account to debit", example = "1000000001")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
        String sourceAccountNumber,
        @Schema(description = "Beneficiary account", example = "1000000002")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
        String destinationAccountNumber,
        @Schema(description = "Amount > 0 with at most 2 decimals", example = "1000000.00")
        @NotNull BigDecimal amount,
        @Schema(description = "Only VND is supported in v1", example = "VND")
        @NotBlank String currency,
        @Schema(description = "Optional note, max 255 chars", example = "Dinner payment", nullable = true)
        @Size(max = 255) String description) {
}
