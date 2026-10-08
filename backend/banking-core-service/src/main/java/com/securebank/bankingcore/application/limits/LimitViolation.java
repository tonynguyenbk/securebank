package com.securebank.bankingcore.application.limits;

import com.securebank.common.error.ErrorCode;

/** Why a transfer amount is outside the account's limits (TRANSFER_LIMIT_EXCEEDED or DAILY_LIMIT_EXCEEDED). */
public record LimitViolation(ErrorCode code, String message) {
}
