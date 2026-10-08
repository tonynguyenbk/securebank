package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.application.audit.AuditActions;
import com.securebank.bankingcore.application.audit.AuditRecorder;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.TransactionFailedEvent;
import com.securebank.common.outbox.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integration events + audit records of a transfer, written to the outbox inside the transfer's own
 * transaction (never sent to Kafka directly): if the transfer commits, its events are guaranteed to be
 * published eventually (at-least-once); if it rolls back, no event exists.
 */
@Component
public class TransferEvents {

    static final String AGGREGATE_TRANSACTION = "TRANSACTION";

    private final OutboxWriter outbox;
    private final AuditRecorder audit;

    public TransferEvents(OutboxWriter outbox, AuditRecorder audit) {
        this.outbox = outbox;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(BankTransaction tx, Account source, Account destination, Actor actor) {
        Customer sender = source.getCustomer();
        Customer recipient = destination.getCustomer();
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                Events.newId(), TransactionCompletedEvent.TYPE, Events.VERSION_1, tx.getCompletedAt(),
                tx.getId(), tx.getTransactionReference(),
                source.getId(), source.getAccountNumber(),
                destination.getId(), destination.getAccountNumber(),
                sender.getId(), sender.getFullName(), sender.getUserId(),
                recipient.getId(), recipient.getFullName(), recipient.getUserId(),
                tx.getAmount(), tx.getCurrency(), tx.getDescription(),
                source.getBalance(), destination.getBalance());
        outbox.append(AGGREGATE_TRANSACTION, tx.getId(), Topics.TRANSACTION_COMPLETED, source.getId().toString(),
                event);
        audit.success(actor, AuditActions.TRANSFER_COMPLETED, AuditActions.RESOURCE_TRANSACTION, tx.getId(),
                null, details(tx, source));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejected(BankTransaction tx, Account source, Actor actor) {
        Customer sender = source.getCustomer();
        TransactionFailedEvent event = new TransactionFailedEvent(
                Events.newId(), TransactionFailedEvent.TYPE, Events.VERSION_1, tx.getCreatedAt(),
                tx.getId(), tx.getTransactionReference(),
                source.getId(), source.getAccountNumber(), tx.getDestinationAccountNumber(),
                sender.getId(), sender.getUserId(),
                tx.getAmount(), tx.getCurrency(), tx.getStatus().name(), tx.getFailureCode(), tx.getFailureReason());
        outbox.append(AGGREGATE_TRANSACTION, tx.getId(), Topics.TRANSACTION_FAILED, source.getId().toString(), event);
        Map<String, Object> after = details(tx, source);
        after.put("failureCode", tx.getFailureCode());
        after.put("failureReason", tx.getFailureReason());
        audit.failure(actor, AuditActions.TRANSFER_REJECTED, AuditActions.RESOURCE_TRANSACTION, tx.getId(),
                null, after);
    }

    private static Map<String, Object> details(BankTransaction tx, Account source) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionReference", tx.getTransactionReference());
        details.put("status", tx.getStatus().name());
        details.put("amount", tx.getAmount());
        details.put("currency", tx.getCurrency());
        details.put("sourceAccountNumber", source.getAccountNumber());
        details.put("destinationAccountNumber", tx.getDestinationAccountNumber());
        return details;
    }
}
