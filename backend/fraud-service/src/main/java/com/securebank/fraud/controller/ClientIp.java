package com.securebank.fraud.controller;

import jakarta.servlet.http.HttpServletRequest;

/** Caller IP for audit records: first X-Forwarded-For hop (set by the gateway), else the socket address. */
final class ClientIp {

    private static final int MAX_LENGTH = 64;

    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first.length() > MAX_LENGTH ? first.substring(0, MAX_LENGTH) : first;
            }
        }
        return request.getRemoteAddr();
    }
}
