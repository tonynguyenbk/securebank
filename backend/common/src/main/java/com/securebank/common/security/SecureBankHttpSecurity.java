package com.securebank.common.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Baseline applied by every servlet service's SecurityFilterChain: stateless JWT, no CSRF (no cookies),
 * secure headers, JSON 401/403, public health + API docs. Services add their own authorizeHttpRequests.
 *
 * <pre>
 * http = secureBankHttpSecurity.apply(http);
 * http.authorizeHttpRequests(a -> a.requestMatchers("/api/v1/x/**").authenticated().anyRequest().denyAll());
 * return http.build();
 * </pre>
 */
public class SecureBankHttpSecurity {

    public static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtFilter;
    private final SecurityErrorHandlers errorHandlers;

    public SecureBankHttpSecurity(JwtAuthenticationFilter jwtFilter, SecurityErrorHandlers errorHandlers) {
        this.jwtFilter = jwtFilter;
        this.errorHandlers = errorHandlers;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                // CORS is handled once at the API gateway; services are not called by browsers directly.
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandlers.entryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
