package com.securebank.identity.application.token;

import com.securebank.common.security.JwtClaims;
import com.securebank.common.security.JwtProperties;
import com.securebank.common.security.JwtTokenValidator;
import com.securebank.common.security.Role;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues HS256 access tokens with the claims of api.md §1:
 * {@code sub, username, roles, jti, typ=access, iss, iat, exp}. Verified by {@link JwtTokenValidator} in every service.
 */
@Component
public class AccessTokenIssuer {

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenIssuer(JwtProperties properties, Clock clock) {
        this.key = JwtTokenValidator.signingKey(properties.secret());
        this.issuer = properties.issuer();
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, String username, Collection<Role> roles) {
        // JWT timestamps have second precision; truncate so "exp" matches what we report.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        String tokenId = UUID.randomUUID().toString();
        List<String> roleNames = roles.stream().map(Enum::name).sorted().toList();

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtClaims.USERNAME, username)
                .claim(JwtClaims.ROLES, roleNames)
                .id(tokenId)
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                // Explicit HS256: a ≥64-byte secret would otherwise make jjwt pick HS512.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, tokenId, issuedAt, expiresAt);
    }

    public Duration ttl() {
        return ttl;
    }
}
