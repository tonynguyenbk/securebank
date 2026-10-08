package com.securebank.gateway;

import com.securebank.gateway.support.EchoDownstream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability // enables the Prometheus registry, which @SpringBootTest disables by default
class GatewayIntegrationTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String downstream = EchoDownstream.url();
        registry.add("IDENTITY_URL", () -> downstream);
        registry.add("BANKING_CORE_URL", () -> downstream);
        registry.add("AUDIT_URL", () -> downstream);
        registry.add("NOTIFICATION_URL", () -> downstream);
        // nothing listens on port 1: "service down"
        registry.add("FRAUD_URL", () -> "http://localhost:1");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000, http://app.example.test");
        // small buckets so the test can exhaust them quickly
        registry.add("RATE_LIMIT_LOGIN_REPLENISH_RATE", () -> "1");
        registry.add("RATE_LIMIT_LOGIN_BURST_CAPACITY", () -> "2");
        registry.add("RATE_LIMIT_TRANSFER_REPLENISH_RATE", () -> "1");
        registry.add("RATE_LIMIT_TRANSFER_BURST_CAPACITY", () -> "2");
    }

    @Autowired
    WebTestClient client;

    @Test
    void routesToDownstreamAndForwardsAuthorizationUnchanged() {
        client.get().uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer abc.def.ghi")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/accounts/123/balance")
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.authorization").isEqualTo("Bearer abc.def.ghi")
                .jsonPath("$.forwardedFor").isEqualTo("127.0.0.1");
    }

    @Test
    void forgedForwardedForIsNotPassedOn() {
        // Only X-Forwarded-For entries matching trusted-proxies survive; the real peer address is appended.
        // A client therefore cannot pick the IP that identity-service rate-limits and audits.
        client.get().uri("/api/v1/customers/me")
                .header("X-Forwarded-For", "203.0.113.9")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.forwardedFor").isEqualTo("127.0.0.1");
    }

    @Test
    void everyContractPrefixIsRouted() {
        for (String path : List.of("/api/v1/auth/me", "/api/v1/customers/me", "/api/v1/transfers",
                "/api/v1/admin/stats/today", "/api/v1/audit/logs", "/api/v1/notifications/me")) {
            client.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo(path);
        }
    }

    @Test
    void validIncomingCorrelationIdIsPropagatedAndEchoedOnce() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/v1/customers/me")
                .header("X-Correlation-Id", "frontend-req-12345")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.correlationId").isEqualTo("frontend-req-12345")
                .returnResult();
        assertThat(result.getResponseHeaders().get("X-Correlation-Id")).containsExactly("frontend-req-12345");
    }

    @Test
    void missingOrUnsafeCorrelationIdIsReplacedWithGeneratedUuid() {
        for (String incoming : new String[]{null, "short", "bad id with spaces", "evil{json}value", "x".repeat(65)}) {
            var spec = client.get().uri("/api/v1/customers/me");
            if (incoming != null) {
                spec = spec.header("X-Correlation-Id", incoming);
            }
            EntityExchangeResult<byte[]> result = spec.exchange().expectStatus().isOk()
                    .expectBody().returnResult();
            String echoed = result.getResponseHeaders().getFirst("X-Correlation-Id");
            assertThat(echoed).matches("[0-9a-f-]{36}");
            assertThat(new String(result.getResponseBody())).contains("\"correlationId\":\"" + echoed + "\"");
        }
    }

    @Test
    void downstreamErrorsPassThroughUnchanged() {
        client.get().uri("/api/v1/accounts/conflict").exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().json(EchoDownstream.DOWNSTREAM_ERROR, true);
    }

    @Test
    void unknownRouteIs404ApiError() {
        client.get().uri("/api/v1/nope").header("X-Correlation-Id", "corr-404-test")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-404-test")
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.path").isEqualTo("/api/v1/nope")
                .jsonPath("$.correlationId").isEqualTo("corr-404-test")
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    void unavailableServiceIs503ApiError() {
        client.get().uri("/api/v1/fraud/alerts").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.correlationId").isNotEmpty()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void corsPreflightFromAllowedOrigin() {
        client.options().uri("/api/v1/transfers")
                .header("Origin", "http://app.example.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type, idempotency-key, x-correlation-id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://app.example.test")
                .expectHeader().value("Access-Control-Allow-Methods", v -> assertThat(v).contains("POST"))
                .expectHeader().value("Access-Control-Allow-Headers",
                        v -> assertThat(v.toLowerCase()).contains("idempotency-key").contains("authorization"))
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials");
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() {
        client.options().uri("/api/v1/transfers")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    @Test
    void corsActualRequestExposesCorrelationHeaders() {
        client.get().uri("/api/v1/customers/me")
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
                .expectHeader().value("Access-Control-Expose-Headers",
                        v -> assertThat(v).contains("X-Correlation-Id").contains("Idempotent-Replayed"));
    }

    @Test
    void secureHeadersArePresent() {
        client.get().uri("/api/v1/customers/me").exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void loginIsRateLimitedWithApiErrorBody() {
        assertRateLimited("/api/v1/auth/login", "{\"username\":\"x\",\"password\":\"y\"}");
    }

    @Test
    void transferCreationIsRateLimitedButReadsAreNot() {
        assertRateLimited("/api/v1/transfers", "{}");
        for (int i = 0; i < 5; i++) {
            client.get().uri("/api/v1/transfers").exchange().expectStatus().isOk();
        }
    }

    @Test
    void healthAndPrometheusAreExposedButGatewayActuatorIsNot() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
        client.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    }

    private void assertRateLimited(String path, String body) {
        List<Integer> statuses = new ArrayList<>();
        EntityExchangeResult<byte[]> limited = null;
        for (int i = 0; i < 8; i++) {
            EntityExchangeResult<byte[]> result = client.method(HttpMethod.POST).uri(path)
                    .header("X-Correlation-Id", "corr-rate-limit-" + i)
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .exchange().expectBody().returnResult();
            statuses.add(result.getStatus().value());
            if (result.getStatus().value() == 429 && limited == null) {
                limited = result;
            }
        }
        // Buckets are per client IP (shared by both limited routes), so a previous test may have used tokens.
        assertThat(statuses).contains(429).allMatch(s -> s == 200 || s == 429);
        assertThat(limited).isNotNull();
        assertThat(limited.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String json = new String(limited.getResponseBody());
        assertThat(json).contains("\"status\":429").contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"path\":\"" + path + "\"").contains("\"correlationId\":\"corr-rate-limit-")
                .contains("\"message\":").contains("\"timestamp\":");
    }
}
