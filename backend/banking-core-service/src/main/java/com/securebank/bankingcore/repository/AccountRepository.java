package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    @Query("""
            select new com.securebank.bankingcore.repository.AccountRef(a.id, a.accountNumber, c.userId)
            from Account a join a.customer c
            where a.accountNumber = :accountNumber
            """)
    Optional<AccountRef> findRefByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * {@code SELECT ... FOR UPDATE} on the account row only (no join, so customer rows are never locked and
     * the lock order stays exactly the order in which callers lock accounts). Waiting is bounded by the
     * transaction's {@code lock_timeout}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Query("select a from Account a join fetch a.customer where a.id = :id")
    Optional<Account> findWithCustomerById(@Param("id") UUID id);

    @Query("select a from Account a join fetch a.customer where a.accountNumber = :accountNumber")
    Optional<Account> findWithCustomerByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("select a from Account a where a.customer.id = :customerId order by a.createdAt asc, a.accountNumber asc")
    List<Account> findByCustomerId(@Param("customerId") UUID customerId);

    @Query("""
            select a from Account a join fetch a.customer c
            where c.userId = :userId
            order by a.createdAt asc, a.accountNumber asc
            """)
    List<Account> findByOwnerUserId(@Param("userId") UUID userId);

    @Query("select a.id from Account a where a.customer.userId = :userId")
    List<UUID> findIdsByOwnerUserId(@Param("userId") UUID userId);

    @Query("select a from Account a join fetch a.customer where a.customer.id in :customerIds")
    List<Account> findWithCustomerByCustomerIdIn(@Param("customerIds") List<UUID> customerIds);

    long countByStatus(AccountStatus status);

    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumber();

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);
}
