package com.securebank.bankingcore.application.onboarding;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Demo customers with the fixed UUIDs of docs/contracts/api.md §7, so identity-service and banking-core seed
 * independently and still match. Insert-if-absent (idempotent). Opening balances are set directly — the
 * ledger records movements from transfers onward (documented in the contract). Local demo data only.
 */
@Service
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    static final List<DemoCustomer> DEMO_CUSTOMERS = List.of(
            new DemoCustomer(UUID.fromString("00000000-0000-4000-8000-000000000101"),
                    UUID.fromString("00000000-0000-4000-8000-000000001101"),
                    UUID.fromString("00000000-0000-4000-8000-000000002101"),
                    "Nguyễn Văn An", "customer1@demo.securebank.local", "1000000001", new BigDecimal("25000000.00")),
            new DemoCustomer(UUID.fromString("00000000-0000-4000-8000-000000000102"),
                    UUID.fromString("00000000-0000-4000-8000-000000001102"),
                    UUID.fromString("00000000-0000-4000-8000-000000002102"),
                    "Trần Thị Bình", "customer2@demo.securebank.local", "1000000002", new BigDecimal("10000000.00")));

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransferLimitService limits;
    private final BusinessCalendar calendar;

    public DemoSeedService(CustomerRepository customers, AccountRepository accounts, TransferLimitService limits,
                           BusinessCalendar calendar) {
        this.customers = customers;
        this.accounts = accounts;
        this.limits = limits;
        this.calendar = calendar;
    }

    @Transactional
    public int seed() {
        int created = 0;
        for (DemoCustomer demo : DEMO_CUSTOMERS) {
            if (customers.existsById(demo.customerId()) || customers.existsByUserId(demo.userId())) {
                continue;
            }
            var now = calendar.now();
            Customer customer = customers.save(new Customer(demo.customerId(), demo.userId(), demo.fullName(),
                    demo.email(), null, now));
            Account account = accounts.save(new Account(demo.accountId(), customer, demo.accountNumber(), Money.VND,
                    demo.openingBalance(), now));
            limits.openDefault(account);
            created++;
        }
        if (created > 0) {
            log.info("Demo seed: created {} demo customers/accounts", created);
        }
        return created;
    }

    record DemoCustomer(UUID userId, UUID customerId, UUID accountId, String fullName, String email,
                        String accountNumber, BigDecimal openingBalance) {
    }
}
