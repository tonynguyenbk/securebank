package com.securebank.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates "Authorization: Bearer" requests. Invalid tokens leave the request anonymous;
 * the entry point then answers 401 for protected endpoints. Raw tokens are never logged.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenValidator validator;
    private final TokenRevocationChecker revocationChecker;

    public JwtAuthenticationFilter(JwtTokenValidator validator, TokenRevocationChecker revocationChecker) {
        this.validator = validator;
        this.revocationChecker = revocationChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            validator.validate(header.substring(BEARER.length()).trim())
                    .filter(user -> !revocationChecker.isRevoked(user.tokenId()))
                    .ifPresent(user -> {
                        var authorities = user.roles().stream()
                                .map(r -> new SimpleGrantedAuthority(r.authority()))
                                .toList();
                        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
