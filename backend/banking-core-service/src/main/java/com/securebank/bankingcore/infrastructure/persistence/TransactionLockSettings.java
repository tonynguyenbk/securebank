package com.securebank.bankingcore.infrastructure.persistence;

import com.securebank.bankingcore.config.BankingProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounds every lock wait of the current transaction with PostgreSQL's {@code SET LOCAL lock_timeout}
 * (reset automatically at commit/rollback). Hibernate's lock-timeout hint is not honoured on PostgreSQL for
 * values other than NOWAIT, so the session setting is the reliable way. A timed-out wait raises
 * SQLSTATE 55P03, translated by Spring into a {@code PessimisticLockingFailureException}.
 */
@Component
public class TransactionLockSettings {

    private final JdbcTemplate jdbc;
    private final long timeoutMillis;

    public TransactionLockSettings(JdbcTemplate jdbc, BankingProperties properties) {
        this.jdbc = jdbc;
        this.timeoutMillis = properties.lockTimeout().toMillis();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyLockTimeout() {
        // value is a validated long, never user input
        jdbc.execute("SET LOCAL lock_timeout = '" + timeoutMillis + "ms'");
    }
}
