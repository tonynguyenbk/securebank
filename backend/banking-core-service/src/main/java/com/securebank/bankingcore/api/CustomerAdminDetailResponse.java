package com.securebank.bankingcore.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code CustomerAdmin & { accounts: AccountAdmin[] }}. */
public record CustomerAdminDetailResponse(UUID id, UUID userId, String fullName, String email, String phone,
                                          long accountCount, Instant createdAt, List<AccountAdminResponse> accounts) {
}
