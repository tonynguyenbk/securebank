package com.securebank.identity.application.auth;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.identity.config.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Failed-login throttling in Redis (api.md §1): at most N failures per window per username and per
 * client IP; further attempts get 429 {@code AUTH_LOGIN_RATE_LIMITED} until the window expires.
 * A successful login clears the username counter (the IP counter keeps counting to slow down
 * password spraying across many usernames).
 * <p>
 * Fixed window: the first failure starts the TTL. If Redis is unavailable the limiter fails open
 * (logins keep working, a warning is logged) — the gateway's request rate limiter still applies.
 */
@Component
public class LoginAttemptLimiter {

    static final String USER_KEY_PREFIX = "auth:login:failures:user:";
    static final String IP_KEY_PREFIX = "auth:login:failures:ip:";

    /** INCR + set expiry on the first increment, atomically. */
    static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final StringRedisTemplate redis;
    private final IdentityProperties.LoginRateLimit settings;

    public LoginAttemptLimiter(StringRedisTemplate redis, IdentityProperties properties) {
        this.redis = redis;
        this.settings = properties.loginRateLimit();
    }

    /** @throws ApiException AUTH_LOGIN_RATE_LIMITED when the username or the IP is over the limit */
    public void checkAllowed(String username, String ipAddress) {
        try {
            List<String> counts = redis.opsForValue().multiGet(List.of(userKey(username), ipKey(ipAddress)));
            if (counts != null && counts.stream().anyMatch(this::overLimit)) {
                throw new ApiException(ErrorCode.AUTH_LOGIN_RATE_LIMITED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Login rate limiter unavailable, allowing attempt: {}", e.getMessage());
        }
    }

    public void recordFailure(String username, String ipAddress) {
        String windowMillis = String.valueOf(settings.window().toMillis());
        try {
            redis.execute(INCREMENT_WITH_TTL, List.of(userKey(username)), windowMillis);
            redis.execute(INCREMENT_WITH_TTL, List.of(ipKey(ipAddress)), windowMillis);
        } catch (RuntimeException e) {
            log.warn("Could not record failed login attempt: {}", e.getMessage());
        }
    }

    public void recordSuccess(String username) {
        try {
            redis.delete(userKey(username));
        } catch (RuntimeException e) {
            log.warn("Could not clear failed login counter: {}", e.getMessage());
        }
    }

    private boolean overLimit(String value) {
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= settings.maxFailures();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String userKey(String username) {
        return USER_KEY_PREFIX + username;
    }

    static String ipKey(String ipAddress) {
        return IP_KEY_PREFIX + ipAddress;
    }
}
