package com.securebank.bankingcore.security;

import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.security.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipGuardTest {

    private final OwnershipGuard guard = new OwnershipGuard();

    @Test
    void ownerPassesAndOthersGetAccountNotOwned() {
        UUID owner = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(),
                new Customer(UUID.randomUUID(), owner, "Owner", "o@x", null, Instant.now()),
                "1000000101", "VND", BigDecimal.ZERO, Instant.now());

        assertThatCode(() -> guard.requireOwner(user(owner), account)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireOwner(user(UUID.randomUUID()), account))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_OWNED));
    }

    @Test
    void transactionVisibility() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<UUID> myAccounts = List.of(mine);

        assertThat(guard.canViewTransaction(myAccounts, mine, other, false)).isTrue();
        assertThat(guard.canViewTransaction(myAccounts, mine, other, true)).isTrue();   // own rejection
        assertThat(guard.canViewTransaction(myAccounts, other, mine, false)).isTrue();  // incoming
        assertThat(guard.canViewTransaction(myAccounts, other, mine, true)).isFalse();  // sender's rejection
        assertThat(guard.canViewTransaction(myAccounts, other, UUID.randomUUID(), false)).isFalse();
        assertThat(guard.canViewTransaction(myAccounts, other, null, false)).isFalse();
    }

    private static AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(id, "u", Set.of(Role.CUSTOMER), "jti", Instant.now().plusSeconds(60));
    }
}
