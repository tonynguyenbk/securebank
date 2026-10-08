package com.securebank.bankingcore.api;

import java.time.Instant;
import java.util.UUID;

/** Contract type {@code CustomerAdmin}. */
public record CustomerAdminResponse(UUID id, UUID userId, String fullName, String email, String phone,
                                    long accountCount, Instant createdAt) {
}
