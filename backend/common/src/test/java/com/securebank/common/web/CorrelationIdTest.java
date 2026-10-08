package com.securebank.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Test
    void keepsSafeClientId() {
        assertThat(CorrelationId.sanitizeOrGenerate("abc-123_DEF.456")).isEqualTo("abc-123_DEF.456");
    }

    @Test
    void replacesUnsafeOrMissingId() {
        assertThat(CorrelationId.sanitizeOrGenerate("bad\nlog-injection")).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate(null)).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate("short")).hasSize(36);
    }

    @Test
    void masksAccountNumbers() {
        assertThat(Masking.accountNumber("1000000001")).isEqualTo("******0001");
        assertThat(Masking.accountNumber("12")).isEqualTo("****");
    }
}
