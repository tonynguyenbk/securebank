package com.securebank.notification.config;

import com.securebank.common.security.SecureBankHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless JWT; every /api/v1 endpoint needs a token, roles are checked per method with @PreAuthorize. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecureBankHttpSecurity secureBank)
            throws Exception {
        secureBank.apply(http).authorizeHttpRequests(a -> a
                .requestMatchers(SecureBankHttpSecurity.PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll());
        return http.build();
    }
}
