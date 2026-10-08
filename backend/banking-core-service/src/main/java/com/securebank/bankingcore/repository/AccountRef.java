package com.securebank.bankingcore.repository;

import java.util.UUID;

/**
 * Lightweight, non-managed view of an account used to resolve account numbers before locking.
 * Deliberately not an entity: loading the entity before {@code SELECT ... FOR UPDATE} would put a possibly
 * stale balance into the persistence context, and Hibernate would keep that stale copy after the lock.
 */
public record AccountRef(UUID id, String accountNumber, UUID ownerUserId) {
}
