package com.securebank.bankingcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Banking-core settings ({@code securebank.banking.*}).
 *
 * @param businessZone               zone that defines "today" (daily limit, references, date filters)
 * @param defaultPerTransactionLimit limit given to newly opened accounts
 * @param defaultDailyLimit          daily limit given to newly opened accounts
 * @param lockTimeout                max wait for a row lock (account rows, idempotency key) inside a transfer
 * @param idempotencyRetention       how long a stored transfer response can be replayed
 */
@ConfigurationProperties(prefix = "securebank.banking")
public record BankingProperties(ZoneId businessZone, BigDecimal defaultPerTransactionLimit,
                                BigDecimal defaultDailyLimit, Duration lockTimeout,
                                Duration idempotencyRetention) {

    public BankingProperties {
        if (businessZone == null) {
            businessZone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        if (defaultPerTransactionLimit == null) {
            defaultPerTransactionLimit = new BigDecimal("100000000.00");
        }
        if (defaultDailyLimit == null) {
            defaultDailyLimit = new BigDecimal("500000000.00");
        }
        if (lockTimeout == null) {
            lockTimeout = Duration.ofSeconds(5);
        }
        if (idempotencyRetention == null) {
            idempotencyRetention = Duration.ofHours(24);
        }
    }
}
