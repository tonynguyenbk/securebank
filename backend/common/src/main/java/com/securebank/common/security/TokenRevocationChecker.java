package com.securebank.common.security;

/**
 * Checks whether an access token's ID (jti) was revoked by logout.
 * Default: no check (tokens are short-lived). Enable the Redis implementation with
 * {@code securebank.security.revocation-check=true}.
 */
@FunctionalInterface
public interface TokenRevocationChecker {

    /** Redis key prefix written by identity-service on logout; value TTL = remaining token lifetime. */
    String DENYLIST_PREFIX = "auth:denylist:";

    boolean isRevoked(String tokenId);
}
