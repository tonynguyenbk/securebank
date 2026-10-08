package com.securebank.bankingcore.config;

import com.securebank.common.security.SecureBankHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security (baseline from common). URL rules are a coarse first layer; the authoritative role
 * checks are {@code @PreAuthorize} on every controller method, and ownership checks live in the services.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecureBankHttpSecurity secureBank)
            throws Exception {
        secureBank.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecureBankHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("BANK_STAFF", "AUDITOR", "ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }
}
