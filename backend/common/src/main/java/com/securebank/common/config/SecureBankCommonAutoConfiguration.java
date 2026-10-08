package com.securebank.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.common.error.GlobalExceptionHandler;
import com.securebank.common.json.EventJson;
import com.securebank.common.kafka.CorrelationIdRecordInterceptor;
import com.securebank.common.kafka.ProcessedEventStore;
import com.securebank.common.outbox.OutboxProperties;
import com.securebank.common.outbox.OutboxPublisher;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.security.JwtAuthenticationFilter;
import com.securebank.common.security.JwtProperties;
import com.securebank.common.security.JwtTokenValidator;
import com.securebank.common.security.RedisTokenRevocationChecker;
import com.securebank.common.security.SecureBankHttpSecurity;
import com.securebank.common.security.SecurityErrorHandlers;
import com.securebank.common.security.TokenRevocationChecker;
import com.securebank.common.web.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wires the shared building blocks into every servlet service that depends on {@code common}.
 * Registered in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        KafkaAutoConfiguration.class, RedisAutoConfiguration.class, TransactionAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({JwtProperties.class, OutboxProperties.class})
@Import(GlobalExceptionHandler.class)
public class SecureBankCommonAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public EventJson eventJson(ObjectMapper objectMapper) {
        return new EventJson(objectMapper);
    }

    // ---- security ----

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenValidator jwtTokenValidator(JwtProperties properties) {
        return new JwtTokenValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "securebank.security.revocation-check", havingValue = "false", matchIfMissing = true)
    public TokenRevocationChecker noRevocationCheck() {
        return tokenId -> false;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(name = "securebank.security.revocation-check", havingValue = "true")
    static class RedisRevocationConfiguration {
        @Bean
        @ConditionalOnMissingBean
        TokenRevocationChecker redisTokenRevocationChecker(StringRedisTemplate redis) {
            return new RedisTokenRevocationChecker(redis);
        }
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator validator,
                                                           TokenRevocationChecker revocationChecker) {
        return new JwtAuthenticationFilter(validator, revocationChecker);
    }

    /** The JWT filter runs inside the Spring Security chain only — not as a second servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityErrorHandlers securityErrorHandlers(ObjectMapper objectMapper) {
        return new SecurityErrorHandlers(objectMapper);
    }

    @Bean
    public SecureBankHttpSecurity secureBankHttpSecurity(JwtAuthenticationFilter filter, SecurityErrorHandlers handlers) {
        return new SecureBankHttpSecurity(filter, handlers);
    }

    // ---- persistence-backed messaging helpers ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(JdbcTemplate.class)
    static class JdbcMessagingConfiguration {

        @Bean
        OutboxWriter outboxWriter(JdbcTemplate jdbc, EventJson json) {
            return new OutboxWriter(jdbc, json);
        }

        @Bean
        ProcessedEventStore processedEventStore(JdbcTemplate jdbc) {
            return new ProcessedEventStore(jdbc);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "securebank.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({JdbcTemplate.class, KafkaTemplate.class})
    static class OutboxPublisherConfiguration {

        @Bean
        OutboxPublisher outboxPublisher(JdbcTemplate jdbc, TransactionTemplate tx,
                                        KafkaTemplate<String, String> kafka, OutboxProperties properties,
                                        MeterRegistry meters) {
            return new OutboxPublisher(jdbc, tx, kafka, properties, meters);
        }
    }

    @Bean
    @ConditionalOnClass(KafkaTemplate.class)
    public CorrelationIdRecordInterceptor correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }
}
