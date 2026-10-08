package com.securebank.bankingcore.infrastructure.persistence;

import com.securebank.bankingcore.application.transfer.ReferenceCounter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Atomic per-day counter: a single upsert both creates the day's row and increments it. The row lock taken
 * by the upsert is held until the surrounding transfer transaction ends, so two transfers can never read the
 * same value; the callers take it as their last lock (after the account locks) to keep it short and to keep
 * a consistent global lock order. A rolled-back transfer leaves a gap — references are unique, not gap-free.
 */
@Component
public class JdbcReferenceCounter implements ReferenceCounter {

    private final JdbcTemplate jdbc;

    public JdbcReferenceCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(LocalDate businessDate) {
        Long value = jdbc.queryForObject("""
                INSERT INTO transaction_reference_counters (business_date, last_value)
                VALUES (?, 1)
                ON CONFLICT (business_date)
                DO UPDATE SET last_value = transaction_reference_counters.last_value + 1
                RETURNING last_value
                """, Long.class, Date.valueOf(businessDate));
        if (value == null) {
            throw new IllegalStateException("Reference counter returned no value");
        }
        return value;
    }
}
