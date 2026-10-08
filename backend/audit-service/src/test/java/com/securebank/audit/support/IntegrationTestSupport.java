package com.securebank.audit.support;

import com.securebank.common.security.JwtTokenValidator;
import com.securebank.common.security.Role;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/** Real PostgreSQL and Kafka (Testcontainers), started once per JVM. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String JWT_SECRET = "integration-test-secret-0123456789-abcdefghijklmnop";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("securebank.security.jwt.secret", () -> JWT_SECRET);
        registry.add("securebank.kafka.consumer.initial-backoff", () -> "100ms");
    }

    protected static String bearer(UUID userId, String username, Role... roles) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer("securebank-identity")
                .id(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("roles", Arrays.stream(roles).map(Role::name).toList())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(JwtTokenValidator.signingKey(JWT_SECRET))
                .compact();
        return "Bearer " + token;
    }
}
