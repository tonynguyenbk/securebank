package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.api.CreateTransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    private final RequestHasher hasher = new RequestHasher();
    private final TransferRequestValidator validator = new TransferRequestValidator();

    @Test
    void sameCanonicalRequestHashesIdentically() {
        String a = hash("1000000", "VND", "Dinner");
        String b = hash("1000000.00", "vnd", "  Dinner ");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void anyMeaningfulDifferenceChangesTheHash() {
        String base = hash("1000000", "VND", "Dinner");
        assertThat(hash("1000000.01", "VND", "Dinner")).isNotEqualTo(base);
        assertThat(hash("1000000", "VND", "Lunch")).isNotEqualTo(base);
        assertThat(hash("1000000", "VND", null)).isNotEqualTo(base);
        assertThat(hasher.hash(new TransferCommand("1000000002", "1000000001", new BigDecimal("1000000.00"), "VND",
                "Dinner"))).isNotEqualTo(base);
    }

    @Test
    void fieldBoundariesCannotBeShifted() {
        // without length prefixes "ab|c" and "a|bc" would collide
        String h1 = hasher.hash(new TransferCommand("ab", "c", BigDecimal.ONE, "VND", null));
        String h2 = hasher.hash(new TransferCommand("a", "bc", BigDecimal.ONE, "VND", null));
        assertThat(h1).isNotEqualTo(h2);
    }

    private String hash(String amount, String currency, String description) {
        return hasher.hash(validator.validate(new CreateTransferRequest("1000000001", "1000000002",
                new BigDecimal(amount), currency, description)));
    }
}
