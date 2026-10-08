package com.securebank.identity.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * Network origin of a request, used for rate limiting, audit and refresh-token metadata.
 * Client IP = first value of {@code X-Forwarded-For} (set by the API gateway) if present and well-formed,
 * else the socket's remote address.
 *
 * @param ipAddress client IP (never null, may be "unknown")
 * @param userAgent truncated User-Agent header, or null
 */
public record ClientInfo(String ipAddress, String userAgent) {

    public static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IP_CHARS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(resolveIp(request.getHeader(FORWARDED_FOR), request.getRemoteAddr()),
                truncate(request.getHeader("User-Agent")));
    }

    static String resolveIp(String forwardedFor, String remoteAddr) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (IP_CHARS.matcher(first).matches()) {
                return first;
            }
        }
        if (remoteAddr != null && IP_CHARS.matcher(remoteAddr).matches()) {
            return remoteAddr;
        }
        return "unknown";
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }
}
