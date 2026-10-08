package com.securebank.common.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123456";
    private final JwtProperties props = new JwtProperties(SECRET, "securebank-identity", null, null);
    private final JwtTokenValidator validator = new JwtTokenValidator(props);

    @Test
    void acceptsValidAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = token(SECRET, "securebank-identity", JwtClaims.ACCESS, userId, Instant.now().plusSeconds(60),
                List.of("CUSTOMER", "NOT_A_ROLE"));

        var user = validator.validate(token).orElseThrow();

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.username()).isEqualTo("customer1");
        assertThat(user.roles()).containsExactly(Role.CUSTOMER);
        assertThat(user.tokenId()).isEqualTo("jti-1");
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(SECRET, "securebank-identity", JwtClaims.ACCESS, UUID.randomUUID(),
                Instant.now().minus(Duration.ofMinutes(5)), List.of("CUSTOMER"));
        assertThat(validator.validate(token)).isEmpty();
    }

    @Test
    void rejectsWrongSignature() {
        String token = token("another-secret-another-secret-another-1234", "securebank-identity", JwtClaims.ACCESS,
                UUID.randomUUID(), Instant.now().plusSeconds(60), List.of("ADMIN"));
        assertThat(validator.validate(token)).isEmpty();
    }

    @Test
    void rejectsWrongIssuerAndRefreshTokens() {
        assertThat(validator.validate(token(SECRET, "evil", JwtClaims.ACCESS, UUID.randomUUID(),
                Instant.now().plusSeconds(60), List.of("ADMIN")))).isEmpty();
        assertThat(validator.validate(token(SECRET, "securebank-identity", "refresh", UUID.randomUUID(),
                Instant.now().plusSeconds(60), List.of("ADMIN")))).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(validator.validate("not.a.jwt")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtTokenValidator(new JwtProperties("short", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String token(String secret, String issuer, String type, UUID userId, Instant exp, List<String> roles) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .id("jti-1")
                .claim(JwtClaims.USERNAME, "customer1")
                .claim(JwtClaims.ROLES, roles)
                .claim(JwtClaims.TOKEN_TYPE, type)
                .issuedAt(Date.from(exp.minusSeconds(900)))
                .expiration(Date.from(exp))
                .signWith(JwtTokenValidator.signingKey(secret))
                .compact();
    }
}
