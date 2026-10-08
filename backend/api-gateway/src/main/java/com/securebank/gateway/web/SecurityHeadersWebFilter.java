package com.securebank.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Baseline secure response headers on every gateway response (spec §24). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            if (!headers.containsKey(HttpHeaders.CACHE_CONTROL)) {
                headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
