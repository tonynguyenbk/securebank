package com.securebank.identity.application.token;

import com.securebank.common.security.JwtProperties;
import com.securebank.identity.application.token.RefreshTokenService.RotationResult;
import com.securebank.identity.domain.RefreshToken;
import com.securebank.identity.repository.RefreshTokenRepository;
import com.securebank.identity.security.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "junit");

    @Mock
    RefreshTokenRepository repository;

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        var jwt = new JwtProperties("x".repeat(32), null, null, null);
        service = new RefreshTokenService(repository, hasher, jwt, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rawTokensAre256BitBase64UrlAndUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String raw = hasher.newRawToken();
            assertThat(raw).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64Url.decode(raw)).hasSize(32);
            tokens.add(raw);
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void hashIsDeterministicHexSha256() {
        // SHA-256("abc")
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hasher.hash("abc")).isEqualTo(hasher.hash("abc"));
    }

    @Test
    void issueStoresOnlyTheHashWithSevenDayExpiry() {
        IssuedRefreshToken issued = service.issue(USER, CLIENT);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(hasher.hash(issued.rawToken()))
                .isNotEqualTo(issued.rawToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(issued.toString()).doesNotContain(issued.rawToken());
    }

    @Test
    void rotateConsumesTheTokenAndLinksItsSuccessor() {
        RefreshToken current = token(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(hasher.hash("raw-1"))).thenReturn(Optional.of(current));

        RotationResult result = service.rotate("raw-1", CLIENT);

        assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        var rotated = (RotationResult.Rotated) result;
        assertThat(rotated.userId()).isEqualTo(USER);
        assertThat(rotated.token().rawToken()).isNotEqualTo("raw-1");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedById()).isEqualTo(rotated.token().id());
        verify(repository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void reuseOfARevokedTokenRevokesTheWholeFamily() {
        RefreshToken consumed = token(NOW.plus(Duration.ofDays(1)));
        consumed.rotateTo(UUID.randomUUID(), NOW.minusSeconds(60));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(consumed));
        when(repository.revokeAllActiveForUser(USER, NOW)).thenReturn(2);

        RotationResult result = service.rotate("raw-1", CLIENT);

        assertThat(result).isEqualTo(new RotationResult.ReuseDetected(USER, 2));
        verify(repository).revokeAllActiveForUser(USER, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsInvalidAndNotRotated() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token(NOW)));

        assertThat(service.rotate("raw-1", CLIENT)).isInstanceOf(RotationResult.Invalid.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownTokenIsInvalid() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThat(service.rotate("nope", CLIENT)).isInstanceOf(RotationResult.Invalid.class);
    }

    @Test
    void revokeOnlyAffectsTheOwnersActiveToken() {
        RefreshToken token = token(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThat(service.revoke("raw", UUID.randomUUID())).isFalse();
        assertThat(token.isRevoked()).isFalse();

        assertThat(service.revoke("raw", USER)).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        assertThat(service.revoke("raw", USER)).isFalse();
    }

    private static RefreshToken token(Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), USER, "h".repeat(64), NOW.minusSeconds(3600), expiresAt,
                "10.0.0.1", null);
    }

    private static final class Base64Url {
        static byte[] decode(String value) {
            return java.util.Base64.getUrlDecoder().decode(value);
        }
    }
}
