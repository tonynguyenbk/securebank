package com.securebank.bankingcore.application.transfer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountLockerTest {

    @Test
    void lockOrderIsUnsignedLikePostgres() {
        // UUID.compareTo is signed: it would put 8000... before 0000...; PostgreSQL (and our order) does not
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID high = UUID.fromString("80000000-0000-4000-8000-000000000001");
        assertThat(low.compareTo(high)).isPositive();
        assertThat(AccountLocker.LOCK_ORDER.compare(low, high)).isNegative();
    }
}
