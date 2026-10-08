package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /** {@code pattern} is a lower-case LIKE pattern escaped with '!' (see {@code LikePatterns}). */
    @Query(value = """
            select new com.securebank.bankingcore.repository.CustomerWithAccountCount(
                c, (select count(a) from Account a where a.customer = c))
            from Customer c
            where lower(c.fullName) like :pattern escape '!'
               or lower(c.email) like :pattern escape '!'
               or coalesce(c.phone, '') like :pattern escape '!'
            """,
            countQuery = """
                    select count(c) from Customer c
                    where lower(c.fullName) like :pattern escape '!'
                       or lower(c.email) like :pattern escape '!'
                       or coalesce(c.phone, '') like :pattern escape '!'
                    """)
    Page<CustomerWithAccountCount> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select new com.securebank.bankingcore.repository.CustomerWithAccountCount(
                c, (select count(a) from Account a where a.customer = c))
            from Customer c where c.id = :id
            """)
    Optional<CustomerWithAccountCount> findWithAccountCount(@Param("id") UUID id);
}
