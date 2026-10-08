package com.securebank.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Identity-specific settings.
 *
 * @param loginRateLimit failed-login throttling (api.md §1: 5 failures / 5 min per username and per IP)
 * @param bcryptStrength BCrypt work factor (12)
 */
@ConfigurationProperties(prefix = "securebank.identity")
public record IdentityProperties(LoginRateLimit loginRateLimit, Integer bcryptStrength) {

    public IdentityProperties {
        if (loginRateLimit == null) {
            loginRateLimit = new LoginRateLimit(0, null);
        }
        if (bcryptStrength == null) {
            bcryptStrength = 12;
        }
    }

    public record LoginRateLimit(int maxFailures, Duration window) {
        public LoginRateLimit {
            if (maxFailures <= 0) {
                maxFailures = 5;
            }
            if (window == null) {
                window = Duration.ofMinutes(5);
            }
        }
    }
}
