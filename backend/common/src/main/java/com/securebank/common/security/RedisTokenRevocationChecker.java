package com.securebank.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;

/** Looks up the logout denylist written by identity-service. Fails closed when Redis is unreachable. */
public class RedisTokenRevocationChecker implements TokenRevocationChecker {

    private final StringRedisTemplate redis;

    public RedisTokenRevocationChecker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(DENYLIST_PREFIX + tokenId));
        } catch (RuntimeException e) {
            // Fail closed: if we cannot prove the token is still valid, treat it as revoked.
            return true;
        }
    }
}
