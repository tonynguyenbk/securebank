package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Claims (userId, key) for the current transaction. Returns 1 when this transaction now owns the key and
     * 0 when another <em>committed</em> transaction already owns it. If another transaction holds an
     * uncommitted claim, PostgreSQL blocks this statement on the unique index until that transaction ends
     * (bounded by {@code lock_timeout}) — that wait is what serializes duplicate requests.
     */
    @Modifying
    @Query(value = """
            insert into idempotency_records (id, user_id, idempotency_key, request_hash, created_at, expires_at)
            values (:id, :userId, :key, :hash, :now, :expiresAt)
            on conflict (user_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("userId") UUID userId, @Param("key") String key,
              @Param("hash") String hash, @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
