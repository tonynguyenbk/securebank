package com.securebank.gateway;

import com.securebank.gateway.support.EchoDownstream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** With Redis unreachable the rate limiter fails open: rate-limited routes keep working and health stays UP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedisDownIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_URL", EchoDownstream::url);
        registry.add("BANKING_CORE_URL", EchoDownstream::url);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "300ms");
        registry.add("spring.data.redis.connect-timeout", () -> "300ms");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
    }

    @Autowired
    WebTestClient client;

    @Test
    void loginStillRoutedWhenRedisIsDown() {
        for (int i = 0; i < 3; i++) {
            client.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"x\",\"password\":\"y\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo("/api/v1/auth/login");
        }
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
