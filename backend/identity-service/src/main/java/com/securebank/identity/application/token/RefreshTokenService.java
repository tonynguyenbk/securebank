package com.securebank.identity.application.token;

import com.securebank.common.security.JwtProperties;
import com.securebank.identity.domain.RefreshToken;
import com.securebank.identity.repository.RefreshTokenRepository;
import com.securebank.identity.security.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh-token lifecycle: issue, rotate (one-time use) and revoke.
 * All methods must run inside the caller's transaction.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenHasher hasher;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenHasher hasher,
                               JwtProperties jwtProperties, Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.ttl = jwtProperties.refreshTokenTtl();
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefreshToken issue(UUID userId, ClientInfo client) {
        Instant now = clock.instant();
        String raw = hasher.newRawToken();
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, hasher.hash(raw), now, now.plus(ttl),
                client.ipAddress(), client.userAgent());
        repository.save(token);
        return new IssuedRefreshToken(token.getId(), raw, token.getExpiresAt());
    }

    /**
     * Consumes {@code rawToken} and issues its successor. Reuse of an already revoked token is treated as
     * theft: every active refresh token of that user is revoked. The caller must commit the transaction
     * even for {@link RotationResult.ReuseDetected} so the revocation sticks.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RotationResult rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hasher.hash(rawToken));
        if (found.isEmpty()) {
            return new RotationResult.Invalid();
        }
        RefreshToken current = found.get();
        if (current.isRevoked()) {
            int revoked = repository.revokeAllActiveForUser(current.getUserId(), now);
            log.warn("Refresh token reuse detected for user {} (token {}); revoked {} active refresh token(s)",
                    current.getUserId(), current.getId(), revoked);
            return new RotationResult.ReuseDetected(current.getUserId(), revoked);
        }
        if (current.isExpired(now)) {
            return new RotationResult.Invalid();
        }
        IssuedRefreshToken successor = issue(current.getUserId(), client);
        current.rotateTo(successor.id(), now);
        return new RotationResult.Rotated(current.getUserId(), successor);
    }

    /** Revokes the token if it exists, is active and belongs to {@code userId}. Returns whether it did. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean revoke(String rawToken, UUID userId) {
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hasher.hash(rawToken));
        if (found.isEmpty() || !found.get().getUserId().equals(userId) || found.get().isRevoked()) {
            return false;
        }
        found.get().revoke(clock.instant());
        return true;
    }

    public Duration ttl() {
        return ttl;
    }

    public sealed interface RotationResult {
        record Rotated(UUID userId, IssuedRefreshToken token) implements RotationResult {
        }

        record ReuseDetected(UUID userId, int revokedCount) implements RotationResult {
        }

        record Invalid() implements RotationResult {
        }
    }
}
