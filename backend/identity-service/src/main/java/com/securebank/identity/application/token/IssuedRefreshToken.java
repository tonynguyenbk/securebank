package com.securebank.identity.application.token;

import java.time.Instant;
import java.util.UUID;

/** The raw refresh token value is returned to the client exactly once; only its hash is persisted. */
public record IssuedRefreshToken(UUID id, String rawToken, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[id=" + id + ", expiresAt=" + expiresAt + "]";
    }
}
