package com.securebank.identity.application.auth;

import com.securebank.common.security.TokenRevocationChecker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Writes {@code auth:denylist:<jti>} so every service with revocation checks rejects a logged-out access
 * token before it expires. The key lives exactly as long as the token would have.
 */
@Component
public class AccessTokenDenylist {

    private final StringRedisTemplate redis;
    private final Clock clock;

    public AccessTokenDenylist(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null) {
            return;
        }
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return; // already expired: nothing to deny
        }
        // Round up so the key never disappears before the token expires.
        Duration ttl = Duration.ofSeconds(remaining.toSeconds() + 1);
        redis.opsForValue().set(TokenRevocationChecker.DENYLIST_PREFIX + tokenId, "1", ttl);
    }
}
