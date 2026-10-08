package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID>,
        JpaSpecificationExecutor<BankTransaction> {

    /** List queries fetch both parties and their customers in the same SELECT (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"sourceAccount", "sourceAccount.customer",
            "destinationAccount", "destinationAccount.customer"})
    Page<BankTransaction> findAll(Specification<BankTransaction> spec, Pageable pageable);

    @Query("""
            select t from BankTransaction t
              join fetch t.sourceAccount sa join fetch sa.customer
              left join fetch t.destinationAccount da left join fetch da.customer
            where t.id = :id
            """)
    Optional<BankTransaction> findDetailedById(@Param("id") UUID id);

    /**
     * Sum of outgoing transfers with the given status in [from, to). Used for the daily limit while the
     * caller holds the source account's write lock, so no concurrent transfer from that account can be
     * committed between this read and the debit.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from BankTransaction t
            where t.sourceAccount.id = :accountId and t.status = :status
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumOutgoing(@Param("accountId") UUID accountId, @Param("status") TransactionStatus status,
                           @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(t) from BankTransaction t where t.createdAt >= :from and t.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t) from BankTransaction t
            where t.status in :statuses and t.createdAt >= :from and t.createdAt < :to
            """)
    long countByStatusInCreatedBetween(@Param("statuses") Collection<TransactionStatus> statuses,
                                       @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(t.amount), 0) from BankTransaction t
            where t.status = :status and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumByStatusCreatedBetween(@Param("status") TransactionStatus status,
                                         @Param("from") Instant from, @Param("to") Instant to);

    /** SUCCESS transfers grouped by business day; rows are [date (java.sql.Date), count, sum]. */
    @Query(value = """
            select cast((t.created_at at time zone :zone) as date) as business_date,
                   count(*) as tx_count,
                   coalesce(sum(t.amount), 0) as total
            from bank_transactions t
            where t.status = 'SUCCESS' and t.created_at >= :from and t.created_at < :to
            group by business_date
            order by business_date
            """, nativeQuery = true)
    List<Object[]> dailySuccessTotals(@Param("zone") String zone, @Param("from") Instant from,
                                      @Param("to") Instant to);
}
