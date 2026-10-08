package com.securebank.gateway.config;

import com.securebank.gateway.web.CorrelationIdWebFilter;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewayConfig {

    static {
        // Reactor context "correlationId" <-> SLF4J MDC, so gateway log lines carry the ID
        // (enabled by spring.reactor.context-propagation=auto).
        ContextRegistry.getInstance().registerThreadLocalAccessor(CorrelationIdWebFilter.MDC_KEY,
                () -> MDC.get(CorrelationIdWebFilter.MDC_KEY),
                value -> MDC.put(CorrelationIdWebFilter.MDC_KEY, value),
                () -> MDC.remove(CorrelationIdWebFilter.MDC_KEY));
    }

    /**
     * CORS for the browser frontend, answered once here (services disable CORS). Bearer tokens travel in
     * the Authorization header, so credentials (cookies) are not allowed.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public CorsWebFilter corsWebFilter(@Value("${securebank.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
        cors.setExposedHeaders(List.of("X-Correlation-Id", "Idempotent-Replayed"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }

    /**
     * Rate-limit key = the TCP peer address of the caller. X-Forwarded-For is deliberately not used here:
     * a client could forge it to get a fresh bucket per request.
     */
    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String key = remote == null || remote.getAddress() == null
                    ? "unknown" : remote.getAddress().getHostAddress();
            return Mono.just(key);
        };
    }
}
