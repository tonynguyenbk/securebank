package com.securebank.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes gateway-originated errors in the shared {@code ApiError} shape (docs/contracts/api.md §0).
 * The gateway is reactive and does not depend on the servlet-based {@code common} module, so the
 * error codes are the same names as {@code com.securebank.common.error.ErrorCode}.
 */
@Component
public class ApiErrorWriter {

    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN_OPERATION = "FORBIDDEN_OPERATION";

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] body(ServerWebExchange exchange, HttpStatusCode status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", status.value());
        error.put("code", code);
        error.put("message", message);
        error.put("path", exchange.getRequest().getPath().value());
        String correlationId = CorrelationIdWebFilter.current(exchange);
        if (correlationId != null) {
            error.put("correlationId", correlationId);
        }
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            return ("{\"status\":" + status.value() + ",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Sets status + JSON content type and writes the body on the given response. */
    public Mono<Void> write(ServerWebExchange exchange, ServerHttpResponse response, HttpStatusCode status,
                            String code, String message) {
        byte[] bytes = body(exchange, status, code, message);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
