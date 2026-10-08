package com.securebank.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        String refreshToken,
        @Schema(description = "Refresh token lifetime in seconds", example = "604800") long refreshExpiresIn,
        UserResponse user
) {
    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, user=" + user.username() + "]";
    }
}
