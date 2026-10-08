package com.securebank.common.error;

import org.springframework.http.HttpStatus;

/**
 * Domain error codes shared by every service. The frontend maps these codes to EN/VI messages,
 * so a code is part of the public API contract (docs/contracts/api.md) and must never be renamed.
 */
public enum ErrorCode {
    // generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request is invalid."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body could not be read."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN_OPERATION(HttpStatus.FORBIDDEN, "You are not allowed to perform this operation."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "The resource was changed by someone else. Reload and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),

    // identity
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The token is invalid or expired."),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The refresh token is invalid, expired or revoked."),
    AUTH_USERNAME_TAKEN(HttpStatus.CONFLICT, "This username is already registered."),
    AUTH_LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Try again later."),

    // banking core
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Customer not found."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found."),
    ACCOUNT_NOT_OWNED(HttpStatus.FORBIDDEN, "The account does not belong to the current customer."),
    ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "The account is frozen."),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "The account is closed."),
    ACCOUNT_STATUS_UNCHANGED(HttpStatus.CONFLICT, "The account is already in the requested status."),
    ACCOUNT_BUSY(HttpStatus.CONFLICT, "The account is busy with another operation. Retry with the same Idempotency-Key."),
    SAME_ACCOUNT_TRANSFER(HttpStatus.BAD_REQUEST, "Source and destination accounts must be different."),
    CURRENCY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "The currency is not supported."),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Account currencies do not match."),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds."),
    INVALID_TRANSFER_AMOUNT(HttpStatus.BAD_REQUEST, "The transfer amount is invalid."),
    TRANSFER_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "The amount exceeds the per-transaction limit."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "The amount exceeds the daily transfer limit."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "The Idempotency-Key header is required."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "The idempotency key was already used with a different request."),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this idempotency key is still being processed."),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found."),

    // fraud
    FRAUD_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "Fraud alert not found."),
    FRAUD_ALERT_INVALID_TRANSITION(HttpStatus.CONFLICT, "The fraud alert cannot move to the requested status."),

    // audit / notification
    AUDIT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Audit log not found."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
