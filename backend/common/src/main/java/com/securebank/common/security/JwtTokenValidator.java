package com.securebank.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Verifies signature, issuer, expiry and token type of access tokens. */
public class JwtTokenValidator {

    private final SecretKey key;
    private final String issuer;

    public JwtTokenValidator(JwtProperties properties) {
        this.key = signingKey(properties.secret());
        this.issuer = properties.issuer();
    }

    public static SecretKey signingKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "securebank.security.jwt.secret (env JWT_SECRET) must be set and at least 32 bytes long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<AuthenticatedUser> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!JwtClaims.ACCESS.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(JwtClaims.USERNAME, String.class),
                    roles(claims.get(JwtClaims.ROLES)),
                    claims.getId(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Set<Role> roles(Object raw) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (raw instanceof Collection<?> values) {
            for (Object v : values) {
                try {
                    roles.add(Role.valueOf(String.valueOf(v)));
                } catch (IllegalArgumentException ignored) {
                    // unknown role names are dropped, never granted
                }
            }
        }
        return roles;
    }
}
