package com.securebank.fraud.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code known_beneficiaries}: the durable record of customer → destination account pairs that already
 * received money. Source of truth for NEW_BENEFICIARY; Redis only caches it.
 */
@Repository
public class KnownBeneficiaryRepository {

    private final JdbcTemplate jdbc;

    public KnownBeneficiaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the pair inside the caller's transaction.
     *
     * @return true when the pair was not known before (this is the first transfer)
     */
    public boolean registerIfAbsent(UUID customerId, UUID destinationAccountId, Instant firstSeenAt) {
        int inserted = jdbc.update("""
                INSERT INTO known_beneficiaries (customer_id, destination_account_id, first_seen_at)
                VALUES (?, ?, ?)
                ON CONFLICT (customer_id, destination_account_id) DO NOTHING
                """, customerId, destinationAccountId, Timestamp.from(firstSeenAt));
        return inserted == 1;
    }
}
