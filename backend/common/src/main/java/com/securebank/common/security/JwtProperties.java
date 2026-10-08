package com.securebank.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings. The secret comes from the JWT_SECRET environment variable and is never committed.
 *
 * @param secret          HMAC-SHA256 key, at least 32 bytes
 * @param issuer          expected "iss" claim
 * @param accessTokenTtl  access token lifetime (issuer only)
 * @param refreshTokenTtl refresh token lifetime (issuer only)
 */
@ConfigurationProperties(prefix = "securebank.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "securebank-identity";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(7);
        }
    }
}
