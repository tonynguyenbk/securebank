package com.securebank.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway's {@code RequestRateLimiter} answers 429 with an empty body
 * ({@code response.setComplete()}). This filter decorates the response so that such a body-less 429
 * carries the standard ApiError JSON with code {@code RATE_LIMITED}. Downstream 429s (which come with
 * their own body, e.g. AUTH_LOGIN_RATE_LIMITED) are written through {@code writeWith} and pass unchanged.
 */
@Component
public class RateLimitedResponseFilter implements GlobalFilter, Ordered {

    static final String MESSAGE = "Too many requests. Try again later.";

    private final ApiErrorWriter errorWriter;

    public RateLimitedResponseFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> setComplete() {
                HttpStatusCode status = getStatusCode();
                if (status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value() && !isCommitted()) {
                    return errorWriter.write(exchange, getDelegate(), status, ApiErrorWriter.RATE_LIMITED, MESSAGE);
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
