package com.securebank.common.error;

import com.securebank.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;

public final class ApiErrorFactory {

    private ApiErrorFactory() {
    }

    public static ApiError of(ErrorCode code, String message, HttpServletRequest request) {
        return of(code, message, request, List.of());
    }

    public static ApiError of(ErrorCode code, String message, HttpServletRequest request,
                              List<ApiError.FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                code.status().value(),
                code.name(),
                message,
                request.getRequestURI(),
                CorrelationId.current(),
                fieldErrors);
    }
}
