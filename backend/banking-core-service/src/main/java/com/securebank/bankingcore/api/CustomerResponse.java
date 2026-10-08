package com.securebank.bankingcore.api;

import java.time.Instant;
import java.util.UUID;

/** GET /customers/me (contract §2). */
public record CustomerResponse(UUID id, UUID userId, String fullName, String email, String phone, Instant createdAt) {
}
