package com.securebank.identity.config;

import com.securebank.common.security.SecureBankHttpSecurity;
import com.securebank.identity.security.Bcrypt72SafePasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecureBankHttpSecurity secureBankHttpSecurity)
            throws Exception {
        secureBankHttpSecurity.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecureBankHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(IdentityProperties properties) {
        return new Bcrypt72SafePasswordEncoder(properties.bcryptStrength());
    }
}
