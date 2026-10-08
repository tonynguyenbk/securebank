package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.TransferLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferLimitRepository extends JpaRepository<TransferLimit, UUID> {

    @Query("select l from TransferLimit l where l.account.id = :accountId")
    Optional<TransferLimit> findByAccountId(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TransferLimit l where l.account.id = :accountId")
    Optional<TransferLimit> findByAccountIdForUpdate(@Param("accountId") UUID accountId);
}
