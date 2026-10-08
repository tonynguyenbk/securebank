package com.securebank.identity.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHelpersTest {

    private final Bcrypt72SafePasswordEncoder encoder = new Bcrypt72SafePasswordEncoder(4);

    @Test
    void shortPasswordsUsePlainBcrypt() {
        String hash = encoder.encode("Customer@123");
        assertThat(hash).startsWith("$2a$04$");
        assertThat(encoder.matches("Customer@123", hash)).isTrue();
        assertThat(encoder.matches("Customer@124", hash)).isFalse();
        assertThat(Bcrypt72SafePasswordEncoder.prepare("Customer@123")).isEqualTo("Customer@123");
    }

    @Test
    void passwordsOver72BytesAreFullySignificant() {
        String base = "Aa1!" + "x".repeat(80);
        String hash = encoder.encode(base);
        assertThat(encoder.matches(base, hash)).isTrue();
        // differs only after byte 72: plain BCrypt would accept it, we must not
        assertThat(encoder.matches(base.substring(0, base.length() - 1) + "y", hash)).isFalse();
        // multi-byte characters count as bytes, not chars
        String vietnamese = "Mật khẩu rất dài ".repeat(4) + "A1!";
        assertThat(encoder.matches(vietnamese, encoder.encode(vietnamese))).isTrue();
    }

    @Test
    void clientIpPrefersFirstForwardedForValue() {
        assertThat(ClientInfo.resolveIp("203.0.113.7, 172.18.0.5", "172.18.0.5")).isEqualTo("203.0.113.7");
        assertThat(ClientInfo.resolveIp("2001:db8::1", "127.0.0.1")).isEqualTo("2001:db8::1");
        assertThat(ClientInfo.resolveIp(null, "127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(ClientInfo.resolveIp("  ", "127.0.0.1")).isEqualTo("127.0.0.1");
        // garbage (log/key injection) falls back to the socket address
        assertThat(ClientInfo.resolveIp("evil\nvalue", "127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(ClientInfo.resolveIp(null, null)).isEqualTo("unknown");
    }
}
