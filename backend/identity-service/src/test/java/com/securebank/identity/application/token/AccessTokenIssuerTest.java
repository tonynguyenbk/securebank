package com.securebank.identity.application.token;

import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.security.JwtProperties;
import com.securebank.common.security.JwtTokenValidator;
import com.securebank.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenIssuerTest {

    /** 64 bytes, like a production secret from `openssl rand -base64 48`: must still sign with HS256. */
    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");

    private final JwtProperties properties = new JwtProperties(SECRET, "securebank-identity", null, null);

    @Test
    void issuesTokenWithContractClaims() {
        Instant now = Instant.parse("2026-10-08T03:42:00.789Z");
        AccessTokenIssuer issuer = new AccessTokenIssuer(properties, Clock.fixed(now, ZoneOffset.UTC));

        IssuedAccessToken issued = issuer.issue(USER_ID, "customer1", Set.of(Role.CUSTOMER, Role.ADMIN));

        Claims claims = Jwts.parser().verifyWith(JwtTokenValidator.signingKey(SECRET))
                .clock(() -> java.util.Date.from(now)).build()
                .parseSignedClaims(issued.token()).getPayload();
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("username", String.class)).isEqualTo("customer1");
        assertThat(claims.get("roles", List.class)).containsExactly("ADMIN", "CUSTOMER");
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("securebank-identity");
        assertThat(claims.getId()).isEqualTo(issued.tokenId());
        assertThat(UUID.fromString(claims.getId())).isNotNull();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(Instant.parse("2026-10-08T03:42:00Z"));
        assertThat(claims.getExpiration().toInstant()).isEqualTo(Instant.parse("2026-10-08T03:57:00Z"));
        assertThat(issued.expiresAt()).isEqualTo(claims.getExpiration().toInstant());

        String header = new String(Base64.getUrlDecoder().decode(issued.token().split("\\.")[0]));
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void roundTripsThroughTheSharedValidator() {
        AccessTokenIssuer issuer = new AccessTokenIssuer(properties, Clock.systemUTC());
        IssuedAccessToken issued = issuer.issue(USER_ID, "staff1", Set.of(Role.BANK_STAFF));

        AuthenticatedUser user = new JwtTokenValidator(properties).validate(issued.token()).orElseThrow();

        assertThat(user.userId()).isEqualTo(USER_ID);
        assertThat(user.username()).isEqualTo("staff1");
        assertThat(user.roles()).containsExactly(Role.BANK_STAFF);
        assertThat(user.tokenId()).isEqualTo(issued.tokenId());
        assertThat(user.expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void expiredTokenIsRejectedByValidator() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        IssuedAccessToken issued = new AccessTokenIssuer(properties, past).issue(USER_ID, "x", Set.of(Role.CUSTOMER));

        assertThat(new JwtTokenValidator(properties).validate(issued.token())).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        var other = new JwtProperties("another-secret-another-secret-another-secret!!", "securebank-identity", null, null);
        IssuedAccessToken issued = new AccessTokenIssuer(other, Clock.systemUTC()).issue(USER_ID, "x", Set.of());

        assertThat(new JwtTokenValidator(properties).validate(issued.token())).isEmpty();
    }

    @Test
    void customTtlIsHonouredAndEachTokenHasItsOwnId() {
        var shortLived = new JwtProperties(SECRET, "securebank-identity", Duration.ofMinutes(5), null);
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        AccessTokenIssuer issuer = new AccessTokenIssuer(shortLived, Clock.fixed(now, ZoneOffset.UTC));

        IssuedAccessToken a = issuer.issue(USER_ID, "x", Set.of(Role.CUSTOMER));
        IssuedAccessToken b = issuer.issue(USER_ID, "x", Set.of(Role.CUSTOMER));

        assertThat(a.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));
        assertThat(a.tokenId()).isNotEqualTo(b.tokenId());
        assertThat(a.toString()).doesNotContain(a.token());
    }
}
