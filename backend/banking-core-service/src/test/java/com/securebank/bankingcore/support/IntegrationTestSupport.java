package com.securebank.bankingcore.support;

import com.securebank.bankingcore.application.transfer.TransferFaultInjector;
import com.securebank.common.events.Topics;
import com.securebank.common.security.JwtClaims;
import com.securebank.common.security.JwtTokenValidator;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared base for integration tests: one PostgreSQL 17, Redis 7.4 and Kafka 3.9 container for the whole
 * run (started once, reused by the cached Spring context). Tests never truncate (the ledger is append-only
 * at database level); each test creates its own customers and accounts and asserts on its own rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestSupport.TestBeans.class)
public abstract class IntegrationTestSupport {

    public static final String JWT_SECRET = "integration-test-secret-integration-test-secret-0123456789";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @ServiceConnection
    protected static final KafkaContainer KAFKA =new KafkaContainer("apache/kafka:3.9.1");

    static {
        Startables.deepStart(POSTGRES, REDIS, KAFKA).join();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestBank bank;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ArmableFaultInjector faultInjector;

    @AfterEach
    void disarmFaults() {
        faultInjector.disarm();
    }

    protected static String bearer(UUID userId, String username, String... roles) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer("securebank-identity")
                .id(UUID.randomUUID().toString())
                .claim(JwtClaims.USERNAME, username)
                .claim(JwtClaims.ROLES, List.of(roles))
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(JwtTokenValidator.signingKey(JWT_SECRET))
                .compact();
        return "Bearer " + token;
    }

    protected static String customerToken(TestBank.TestAccount account) {
        return bearer(account.userId(), "customer-" + account.accountNumber(), "CUSTOMER");
    }

    protected static String staffToken() {
        return bearer(UUID.randomUUID(), "staff1", "BANK_STAFF");
    }

    protected static String auditorToken() {
        return bearer(UUID.randomUUID(), "auditor1", "AUDITOR");
    }

    protected static String adminToken() {
        return bearer(UUID.randomUUID(), "admin1", "ADMIN");
    }

    /** Throws right after the debit when armed — proves the transfer rolls back as a whole. */
    public static class ArmableFaultInjector implements TransferFaultInjector {

        private final AtomicBoolean armed = new AtomicBoolean();

        public void arm() {
            armed.set(true);
        }

        public void disarm() {
            armed.set(false);
        }

        @Override
        public void afterDebit(UUID sourceAccountId) {
            if (armed.get()) {
                throw new IllegalStateException("Injected failure after debit of " + sourceAccountId);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        ArmableFaultInjector armableFaultInjector() {
            return new ArmableFaultInjector();
        }

        @Bean
        TestBank testBank(org.springframework.transaction.support.TransactionTemplate tx,
                          com.securebank.bankingcore.repository.CustomerRepository customers,
                          com.securebank.bankingcore.repository.AccountRepository accounts,
                          com.securebank.bankingcore.application.limits.TransferLimitService limits) {
            return new TestBank(tx, customers, accounts, limits);
        }

        @Bean
        KafkaAdmin.NewTopics topics() {
            return new KafkaAdmin.NewTopics(Arrays.stream(Topics.ALL)
                    .map(t -> TopicBuilder.name(t).partitions(1).replicas(1).build())
                    .toArray(NewTopic[]::new));
        }
    }
}
