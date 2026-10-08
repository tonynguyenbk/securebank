package com.securebank.bankingcore.integration;

import com.securebank.bankingcore.application.onboarding.DemoSeedService;
import com.securebank.bankingcore.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo seed creates the contract §7 customers with fixed IDs, UTF-8 names and balances, idempotently. */
class DemoSeedIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private DemoSeedService seed;

    @Test
    void seedIsIdempotentAndMatchesTheContract() {
        seed.seed();
        assertThat(seed.seed()).isZero();

        Map<String, Object> an = jdbc.queryForMap("""
                SELECT c.id AS customer_id, c.full_name, c.email, a.id AS account_id, a.account_number, a.balance
                FROM customers c JOIN accounts a ON a.customer_id = c.id WHERE c.user_id = ?
                """, UUID.fromString("00000000-0000-4000-8000-000000000101"));
        assertThat(an.get("customer_id")).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000001101"));
        assertThat(an.get("account_id")).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000002101"));
        assertThat(an.get("full_name")).isEqualTo("Nguyễn Văn An");
        assertThat(an.get("email")).isEqualTo("customer1@demo.securebank.local");
        assertThat(an.get("account_number")).isEqualTo("1000000001");
        assertThat((java.math.BigDecimal) an.get("balance")).isEqualByComparingTo("25000000");

        Map<String, Object> binh = jdbc.queryForMap("""
                SELECT c.full_name, a.account_number, a.balance
                FROM customers c JOIN accounts a ON a.customer_id = c.id WHERE c.user_id = ?
                """, UUID.fromString("00000000-0000-4000-8000-000000000102"));
        assertThat(binh.get("full_name")).isEqualTo("Trần Thị Bình");
        assertThat(binh.get("account_number")).isEqualTo("1000000002");
        assertThat((java.math.BigDecimal) binh.get("balance")).isEqualByComparingTo("10000000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfer_limits WHERE account_id IN (?, ?)",
                Integer.class, UUID.fromString("00000000-0000-4000-8000-000000002101"),
                UUID.fromString("00000000-0000-4000-8000-000000002102"))).isEqualTo(2);
    }
}
