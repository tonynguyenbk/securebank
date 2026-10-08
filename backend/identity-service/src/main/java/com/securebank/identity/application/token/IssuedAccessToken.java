package com.securebank.identity.application.token;

import java.time.Instant;

/** A freshly signed access token. {@code toString} never prints the token itself. */
public record IssuedAccessToken(String token, String tokenId, Instant issuedAt, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken[tokenId=" + tokenId + ", expiresAt=" + expiresAt + "]";
    }
}
