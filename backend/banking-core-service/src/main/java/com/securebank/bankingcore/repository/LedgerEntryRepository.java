package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only ledger: this repository deliberately extends the bare {@link Repository} marker and exposes
 * only insert and read methods — there is no update or delete method to call.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    @Query("select l from LedgerEntry l join fetch l.account where l.transaction.id = :transactionId")
    List<LedgerEntry> findByTransactionId(@Param("transactionId") UUID transactionId);

    @Query("select count(l) from LedgerEntry l where l.transaction.id = :transactionId")
    long countByTransactionId(@Param("transactionId") UUID transactionId);

    /** Passbook lines of one account with the transaction and both parties fetched in the same query. */
    @Query(value = """
            select l from LedgerEntry l
              join fetch l.transaction t
              join fetch t.sourceAccount sa join fetch sa.customer
              left join fetch t.destinationAccount da left join fetch da.customer
            where l.account.id = :accountId and l.createdAt >= :from and l.createdAt < :to
            order by l.createdAt desc, l.id desc
            """,
            countQuery = """
                    select count(l) from LedgerEntry l
                    where l.account.id = :accountId and l.createdAt >= :from and l.createdAt < :to
                    """)
    Page<LedgerEntry> findStatement(@Param("accountId") UUID accountId, @Param("from") Instant from,
                                    @Param("to") Instant to, Pageable pageable);
}
