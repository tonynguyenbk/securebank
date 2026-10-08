package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.api.CreateTransferRequest;
import com.securebank.bankingcore.domain.Money;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stateless checks that need no database access, in the contract's order (§2 "Validation order"):
 * idempotency key → amount → currency → same account. Shape errors (missing fields, bad account number
 * format, description length) were already rejected by Bean Validation with VALIDATION_FAILED.
 */
@Component
public class TransferRequestValidator {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    public String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = key.trim();
        if (!IDEMPOTENCY_KEY.matcher(trimmed).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be 8-100 characters of [A-Za-z0-9_-].");
        }
        return trimmed;
    }

    public TransferCommand validate(CreateTransferRequest request) {
        if (!Money.isValidTransferAmount(request.amount())) {
            throw new ApiException(ErrorCode.INVALID_TRANSFER_AMOUNT,
                    "The amount must be greater than 0 with at most 2 decimal places.");
        }
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        if (!Money.VND.equals(currency)) {
            throw new ApiException(ErrorCode.CURRENCY_NOT_SUPPORTED, "Only VND transfers are supported.");
        }
        String source = request.sourceAccountNumber().trim();
        String destination = request.destinationAccountNumber().trim();
        if (source.equals(destination)) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        return new TransferCommand(source, destination, Money.normalize(request.amount()), currency, description);
    }
}
