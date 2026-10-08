package com.securebank.identity.application.auth;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.identity.config.IdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptLimiterTest {

    @Mock
    StringRedisTemplate redis;
    @Mock
    ValueOperations<String, String> values;

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        var props = new IdentityProperties(new IdentityProperties.LoginRateLimit(5, Duration.ofMinutes(5)), 12);
        limiter = new LoginAttemptLimiter(redis, props);
    }

    @Test
    void allowsWhenNoFailuresRecorded() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of("auth:login:failures:user:alice", "auth:login:failures:ip:10.0.0.1")))
                .thenReturn(Arrays.asList(null, null));

        assertThatCode(() -> limiter.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void allowsBelowTheLimit() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(List.of("4", "4"));

        assertThatCode(() -> limiter.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenUsernameReachedTheLimit() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(Arrays.asList("5", null));

        assertThatThrownBy(() -> limiter.checkAllowed("alice", "10.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.AUTH_LOGIN_RATE_LIMITED);
    }

    @Test
    void blocksWhenIpReachedTheLimit() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(Arrays.asList("1", "7"));

        assertThatThrownBy(() -> limiter.checkAllowed("bob", "10.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> limiter.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.recordFailure("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void recordFailureIncrementsBothCountersWithTheWindowTtl() {
        limiter.recordFailure("alice", "10.0.0.1");

        verify(redis).execute(LoginAttemptLimiter.INCREMENT_WITH_TTL,
                List.of("auth:login:failures:user:alice"), "300000");
        verify(redis).execute(LoginAttemptLimiter.INCREMENT_WITH_TTL,
                List.of("auth:login:failures:ip:10.0.0.1"), "300000");
    }

    @Test
    void successClearsOnlyTheUsernameCounter() {
        limiter.recordSuccess("alice");

        verify(redis).delete("auth:login:failures:user:alice");
        verify(redis, never()).delete(startsWith("auth:login:failures:ip:"));
    }

    @Test
    void defaultsAreFiveFailuresInFiveMinutes() {
        var defaults = new IdentityProperties(null, null);
        assertThat(defaults.loginRateLimit().maxFailures()).isEqualTo(5);
        assertThat(defaults.loginRateLimit().window()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.bcryptStrength()).isEqualTo(12);
    }
}
