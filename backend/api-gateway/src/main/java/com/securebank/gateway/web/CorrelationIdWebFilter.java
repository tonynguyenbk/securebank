package com.securebank.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter of every request (routed, CORS preflight, 404, errors): accepts a safe incoming
 * {@code X-Correlation-Id} (8–64 chars {@code [A-Za-z0-9._-]}) or generates a UUID, forwards it downstream,
 * echoes it on the response and exposes it to logging through the Reactor context (→ MDC "correlationId").
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = sanitizeOrGenerate(exchange.getRequest().getHeaders().getFirst(HEADER));
        exchange.getAttributes().put(ATTRIBUTE, id);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(HEADER, id))
                .build();
        // Set now (visible to CORS/error handlers) and again at commit time, replacing the downstream echo
        // so the client never sees the header twice.
        exchange.getResponse().getHeaders().set(HEADER, id);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, id);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(ctx -> ctx.put(MDC_KEY, id));
    }

    public static String current(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }

    static String sanitizeOrGenerate(String candidate) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
