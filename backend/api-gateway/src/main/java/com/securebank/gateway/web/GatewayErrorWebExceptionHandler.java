package com.securebank.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Errors raised by the gateway itself (no route, downstream unreachable, timeouts) become ApiError JSON
 * without stack traces. Responses produced by downstream services are not exceptions and pass unchanged.
 * Runs before Boot's default handler (order -1).
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ApiErrorWriter errorWriter;

    public GatewayErrorWebExceptionHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        Mapped mapped = map(ex);
        String path = exchange.getRequest().getPath().value();
        // This handler wraps the WebFilter chain, so the Reactor context (and MDC) of CorrelationIdWebFilter
        // is not visible here: set the MDC from the exchange attribute for these log lines.
        String correlationId = CorrelationIdWebFilter.current(exchange);
        try (MDC.MDCCloseable ignored = correlationId == null ? null
                : MDC.putCloseable(CorrelationIdWebFilter.MDC_KEY, correlationId)) {
            if (mapped.status().is5xxServerError()) {
                log.warn("Gateway error {} on {} {}: {}", mapped.status().value(), exchange.getRequest().getMethod(),
                        path, ex.toString());
            } else {
                log.debug("Gateway {} on {}: {}", mapped.status().value(), path, ex.getMessage());
            }
        }
        return errorWriter.write(exchange, exchange.getResponse(), mapped.status(), mapped.code(), mapped.message());
    }

    static Mapped map(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return fromStatus(rse.getStatusCode());
        }
        if (hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)) {
            return new Mapped(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorWriter.INTERNAL_ERROR,
                    "The service is temporarily unavailable. Try again later.");
        }
        if (hasCause(ex, TimeoutException.class) || hasCause(ex, io.netty.handler.timeout.TimeoutException.class)) {
            return new Mapped(HttpStatus.GATEWAY_TIMEOUT, ApiErrorWriter.INTERNAL_ERROR,
                    "The service did not respond in time.");
        }
        if (hasCause(ex, IOException.class)) {
            return new Mapped(HttpStatus.BAD_GATEWAY, ApiErrorWriter.INTERNAL_ERROR,
                    "The service returned an invalid response.");
        }
        return new Mapped(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorWriter.INTERNAL_ERROR,
                "An unexpected error occurred.");
    }

    private static Mapped fromStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 404 || code == 405) {
            return new Mapped(status, ApiErrorWriter.RESOURCE_NOT_FOUND, "The requested resource was not found.");
        }
        if (code == 429) {
            return new Mapped(status, ApiErrorWriter.RATE_LIMITED, RateLimitedResponseFilter.MESSAGE);
        }
        if (code == 401) {
            return new Mapped(status, ApiErrorWriter.UNAUTHENTICATED, "Authentication is required.");
        }
        if (code == 403) {
            return new Mapped(status, ApiErrorWriter.FORBIDDEN_OPERATION,
                    "You are not allowed to perform this operation.");
        }
        if (status.is4xxClientError()) {
            return new Mapped(status, ApiErrorWriter.MALFORMED_REQUEST, "The request could not be processed.");
        }
        if (code == 503) {
            return new Mapped(status, ApiErrorWriter.INTERNAL_ERROR,
                    "The service is temporarily unavailable. Try again later.");
        }
        if (code == 504) {
            return new Mapped(status, ApiErrorWriter.INTERNAL_ERROR, "The service did not respond in time.");
        }
        return new Mapped(status, ApiErrorWriter.INTERNAL_ERROR, "An unexpected error occurred.");
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    record Mapped(HttpStatusCode status, String code, String message) {
    }
}
