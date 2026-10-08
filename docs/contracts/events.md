# SecureBank — Event Contract (Kafka, v1)

**Status: FROZEN for Wave 1.** Java records in `backend/common/src/main/java/com/securebank/common/events/` are the
schema source of truth — producers and consumers both use them, so they cannot drift.

## Topics

| Topic | Record | Producer | Consumers | Key |
|---|---|---|---|---|
| `bank.transaction.completed.v1` | `TransactionCompletedEvent` | banking-core | fraud, notification | sourceAccountId |
| `bank.transaction.failed.v1` | `TransactionFailedEvent` | banking-core | notification | sourceAccountId |
| `bank.account.status.changed.v1` | `AccountStatusChangedEvent` | banking-core | notification | accountId |
| `bank.user.registered.v1` | `UserRegisteredEvent` | identity | banking-core, notification | userId |
| `bank.fraud.alert.created.v1` | `FraudAlertCreatedEvent` | fraud | (ops dashboards, future) | customerId |
| `bank.audit.event.v1` | `AuditEvent` | identity, banking-core, fraud | audit | resourceId |

Consumer group id = the consuming service's `spring.application.name`.

## Envelope

Every payload is a flat JSON object with `eventId` (UUID), `eventType` (e.g. `TRANSACTION_COMPLETED`, `AUDIT`),
`eventVersion` (1), `occurredAt` (ISO instant) plus the record's fields. Money fields are JSON numbers (BigDecimal).

Kafka headers: `eventId`, `eventType`, `X-Correlation-Id` (from the originating HTTP request).
`CorrelationIdRecordInterceptor` (common) puts the correlation ID back into the consumer's MDC automatically.

Example — `bank.transaction.completed.v1`:

```json
{
  "eventId": "6c1d0f0e-3a0e-4c55-9d8a-2a0f6a3b6f11",
  "eventType": "TRANSACTION_COMPLETED",
  "eventVersion": 1,
  "occurredAt": "2026-10-08T03:42:00Z",
  "transactionId": "f36b0ef8-8b35-4fa1-a50a-1b0d0f2cc111",
  "transactionReference": "TX202610080001",
  "sourceAccountId": "00000000-0000-4000-8000-000000002101",
  "sourceAccountNumber": "1000000001",
  "destinationAccountId": "00000000-0000-4000-8000-000000002102",
  "destinationAccountNumber": "1000000002",
  "customerId": "00000000-0000-4000-8000-000000001101",
  "customerName": "Nguyễn Văn An",
  "sourceUserId": "00000000-0000-4000-8000-000000000101",
  "destinationCustomerId": "00000000-0000-4000-8000-000000001102",
  "destinationCustomerName": "Trần Thị Bình",
  "destinationUserId": "00000000-0000-4000-8000-000000000102",
  "amount": 1000000.00,
  "currency": "VND",
  "description": "Demo transfer",
  "sourceBalanceAfter": 24000000.00,
  "destinationBalanceAfter": 11000000.00
}
```

Example — `bank.audit.event.v1`:

```json
{
  "eventId": "…", "eventType": "AUDIT", "eventVersion": 1, "occurredAt": "2026-10-08T03:50:00Z",
  "actorUserId": "00000000-0000-4000-8000-000000000201", "actorUsername": "staff1", "actorRole": "BANK_STAFF",
  "action": "ACCOUNT_FREEZE", "resourceType": "ACCOUNT", "resourceId": "00000000-0000-4000-8000-000000002101",
  "before": {"status": "ACTIVE"}, "after": {"status": "FROZEN", "reason": "Suspected fraud TX202610080007"},
  "correlationId": "…", "ipAddress": "172.18.0.1", "sourceService": "banking-core-service", "outcome": "SUCCESS"
}
```

## Producing — always through the outbox

Never call `KafkaTemplate` from business code. In the same `@Transactional` method as the state change:

```java
outboxWriter.append("TRANSACTION", tx.getId(), Topics.TRANSACTION_COMPLETED, sourceAccountId.toString(), event);
```

`OutboxPublisher` (enabled with `securebank.outbox.enabled=true`) polls every 500 ms, sends with `acks=all` + idempotent
producer, marks rows `PUBLISHED`, and on failure sets `FAILED`, increments `retry_count`, backs off exponentially (max 5 min),
gives up after 10 attempts (row stays `FAILED` for inspection). Metrics: `outbox_pending_count`, `kafka_publish_failure_total`,
`outbox_published_total`.

**Delivery is at-least-once.** If Kafka is down after the DB commit, money has moved and the event waits in `outbox_events`
until Kafka is back — nothing is lost and the transfer API is unaffected.

## Consuming — idempotently

```java
@KafkaListener(topics = Topics.TRANSACTION_COMPLETED)
@Transactional
public void on(String payload) {
    var event = eventJson.read(payload, TransactionCompletedEvent.class);
    if (!processedEvents.markIfFirst(event.eventId(), "fraud-rule-engine")) return; // duplicate delivery
    ...
}
```

Listeners receive the JSON `String` (default `StringDeserializer`) and parse with `EventJson`.
Redis side effects that cannot roll back (fraud counters) must happen **after** `markIfFirst` succeeds, so a duplicate
event never double-counts.

## Shared tables (from `common/src/main/resources/db/common`, enabled by `spring.flyway.locations: classpath:db/migration,classpath:db/common`)

- `V0_1__create_outbox_events.sql` — `outbox_events` (spec §8 columns + `topic`, `event_key`, `correlation_id`, `last_error`, `next_attempt_at`)
- `V0_2__create_processed_events.sql` — `processed_events (event_id, consumer)` primary key

Service migrations start at `V1__…`.
