package com.securebank.common.security;

/** Claim names shared by the issuer (identity-service) and every validator. */
public final class JwtClaims {

    public static final String USERNAME = "username";
    public static final String ROLES = "roles";
    public static final String TOKEN_TYPE = "typ";
    public static final String ACCESS = "access";

    private JwtClaims() {
    }
}
