package com.securebank.fraud.controller;

import com.securebank.common.error.ApiError;
import com.securebank.common.error.ApiErrorFactory;
import com.securebank.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Service-specific mapping that runs before the shared {@code GlobalExceptionHandler}: a concurrent review
 * (optimistic lock conflict) is a 409, not a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FraudExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentReview(OptimisticLockingFailureException ex,
                                                           HttpServletRequest request) {
        ErrorCode code = ErrorCode.FRAUD_ALERT_INVALID_TRANSITION;
        return ResponseEntity.status(code.status()).body(ApiErrorFactory.of(code,
                "The fraud alert was changed by another reviewer. Reload it and try again.", request));
    }
}
