package com.securebank.bankingcore.application.onboarding;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.application.audit.AuditActions;
import com.securebank.bankingcore.application.audit.AuditRecorder;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.CustomerRepository;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.kafka.ProcessedEventStore;
import com.securebank.common.web.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a customer profile + empty VND current account + default limits when identity-service registers a
 * user. Idempotent on two levels: {@link ProcessedEventStore} drops a redelivered event (same eventId), and the
 * unique {@code customers.user_id} (checked first) makes a different event for the same user a no-op.
 */
@Service
public class CustomerOnboardingService {

    static final String CONSUMER = "banking-core-onboarding";
    private static final Logger log = LoggerFactory.getLogger(CustomerOnboardingService.class);

    private final ProcessedEventStore processedEvents;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransferLimitService limits;
    private final AuditRecorder audit;
    private final BusinessCalendar calendar;

    public CustomerOnboardingService(ProcessedEventStore processedEvents, CustomerRepository customers,
                                     AccountRepository accounts, TransferLimitService limits, AuditRecorder audit,
                                     BusinessCalendar calendar) {
        this.processedEvents = processedEvents;
        this.customers = customers;
        this.accounts = accounts;
        this.limits = limits;
        this.audit = audit;
        this.calendar = calendar;
    }

    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (!processedEvents.markIfFirst(event.eventId(), CONSUMER)) {
            log.info("Duplicate UserRegistered event {} ignored", event.eventId());
            return;
        }
        if (customers.existsByUserId(event.userId())) {
            log.info("Customer for user {} already exists; nothing to open", event.userId());
            return;
        }
        var now = calendar.now();
        Customer customer = customers.save(new Customer(UUID.randomUUID(), event.userId(), event.fullName(),
                event.email(), event.phone(), now));
        String number = String.valueOf(accounts.nextAccountNumber());
        Account account = accounts.save(new Account(UUID.randomUUID(), customer, number, Money.VND, Money.zero(), now));
        limits.openDefault(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("accountNumber", number);
        after.put("currency", Money.VND);
        after.put("status", account.getStatus().name());
        after.put("customerId", customer.getId().toString());
        audit.success(Actor.customer(event.userId(), event.username()), AuditActions.ACCOUNT_OPENED,
                AuditActions.RESOURCE_ACCOUNT, account.getId(), null, after);
        log.info("Opened account {} for user {}", Masking.accountNumber(number), event.userId());
    }
}
