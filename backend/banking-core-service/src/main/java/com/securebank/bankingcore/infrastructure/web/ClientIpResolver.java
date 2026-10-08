package com.securebank.bankingcore.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/**
 * Client IP for audit records: the first {@code X-Forwarded-For} hop (set by the API gateway), otherwise the
 * socket's remote address. Returns null outside an HTTP request (e.g. Kafka consumers).
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    /** IPv4 / IPv6 characters only: the header is client-controlled and ends up in the audit log. */
    private static final Pattern SAFE_IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    public String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return resolve(servlet.getRequest());
        }
        return null;
    }

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            if (SAFE_IP.matcher(first).matches()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
