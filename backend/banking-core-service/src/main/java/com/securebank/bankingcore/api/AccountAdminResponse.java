package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code AccountAdmin}. */
public record AccountAdminResponse(UUID id, String accountNumber, UUID customerId, String customerName,
                                   String currency, BigDecimal balance, String status, Instant createdAt,
                                   Instant updatedAt) {
}
