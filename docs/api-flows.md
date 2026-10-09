# API flows

The REST contract (every path, role, DTO and error code) is [`contracts/api.md`](contracts/api.md); this document explains the
flows that matter most.

## 1. Sign-in and token refresh

```mermaid
sequenceDiagram
    participant W as Web app
    participant I as Identity
    participant R as Redis
    W->>I: POST /auth/login {username, password}
    I->>R: failures for username / IP < 5?
    I->>I: BCrypt verify (dummy hash for unknown users: constant time)
    I-->>W: accessToken (JWT, 15 min) + refreshToken (opaque)
    Note over W: access token in memory, refresh token in sessionStorage
    W->>I: POST /auth/refresh {refreshToken}
    I->>I: lock token row, revoke it, issue successor (rotation)
    I-->>W: new pair
    W->>I: POST /auth/refresh with the OLD token
    I->>I: reuse detected → revoke every refresh token of the user
    I-->>W: 401 AUTH_REFRESH_TOKEN_INVALID
```

On logout the refresh token is revoked and the access token's `jti` is written to Redis (`auth:denylist:<jti>`) until it would have
expired; services with Redis reject it immediately.

## 2. Transfer

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant B as Banking core
    participant DB as PostgreSQL
    C->>B: POST /transfers (Idempotency-Key K)
    B->>DB: completed record for (user, K)? → replay
    rect rgb(235, 245, 242)
    Note over B,DB: one transaction
    B->>DB: INSERT idempotency (user, K, hash) — duplicate waits on the unique index
    B->>DB: lock accounts FOR UPDATE in UUID order (lock_timeout 5 s)
    B->>B: validate: owner, ACTIVE, currency, per-tx limit, daily limit, balance
    B->>DB: UPDATE balances · INSERT transaction SUCCESS · 2 ledger entries
    B->>DB: UPDATE idempotency response · INSERT outbox (completed event + audit event)
    end
    B-->>C: 201 TransferResponse
```

Rejection path (insufficient funds, limit, frozen…): the transaction above rolls back; a second transaction stores a `REJECTED`
transaction without ledger entries, the 422 response under the same key, a `TransactionFailedEvent` and a `TRANSFER_REJECTED`
audit event. Validation errors that happen before the source account is identified (bad format, missing key) are not persisted.

| Code | When |
|---|---|
| 400 `IDEMPOTENCY_KEY_REQUIRED` / `VALIDATION_FAILED` / `SAME_ACCOUNT_TRANSFER` / `CURRENCY_NOT_SUPPORTED` | request shape |
| 403 `ACCOUNT_NOT_OWNED`, 404 `ACCOUNT_NOT_FOUND` | ownership / existence |
| 409 `IDEMPOTENCY_KEY_CONFLICT`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, `ACCOUNT_BUSY` | concurrency |
| 422 `ACCOUNT_FROZEN`, `ACCOUNT_CLOSED`, `TRANSFER_LIMIT_EXCEEDED`, `DAILY_LIMIT_EXCEEDED`, `INSUFFICIENT_FUNDS` | business rules (persisted as REJECTED) |

## 3. Retry semantics seen from the client

The web app creates one `Idempotency-Key` per intentional submission and:

- **keeps it** when the request failed in transit (network error, timeout, 5xx, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, `ACCOUNT_BUSY`) —
  "Retry safely" can never send money twice;
- **replaces it** after a success, after a definitive 4xx, or as soon as the user edits any field.

## 4. Event processing (fraud example)

```mermaid
sequenceDiagram
    participant P as Outbox publisher
    participant K as Kafka
    participant F as Fraud consumer
    participant DB as fraud DB
    participant R as Redis
    P->>K: TransactionCompletedEvent (key = source account)
    K->>F: deliver (at least once)
    F->>DB: BEGIN · INSERT processed_events(eventId) — duplicate? stop
    F->>R: update frequency window, daily total, beneficiary set
    F->>F: FraudRuleEngine → score, level
    alt score ≥ 30
        F->>DB: alert + rule hits + timeline OPEN + outbox (alert event, audit event)
    end
    F->>DB: COMMIT
```

Poison messages are retried four times with backoff and then skipped (logged with topic/partition/offset, counted in
`kafka_consumer_skipped_total`) so one bad record can't block a partition.

## 5. Freeze an account

`PATCH /admin/accounts/{id}/freeze {reason}` (BANK_STAFF, ADMIN) → status change, `AccountStatusChangedEvent` and an `ACCOUNT_FREEZE`
audit event with `before: {status: ACTIVE}` / `after: {status: FROZEN, reason}` in one transaction. The notification service tells
the customer the account is frozen without repeating the reason. A frozen account cannot send but can still receive.

## 6. Reconciliation

`GET /admin/reconciliation/transactions/{id}` (AUDITOR, ADMIN) recomputes from the ledger: number of entries, Σ debits, Σ credits,
debit on the source / credit on the destination, both equal to the transaction amount, running balances consistent →
`balanced: true|false`.

## 7. End-to-end demo

`scripts/smoke-test.sh` walks the whole spec scenario through the gateway — sign in, transfer, replay, conflicting replay, limit
rejection, fraud alert from a burst of transfers, freeze, blocked transfer, auditor reconciliation and audit log, auditor blocked
from mutating, cross-customer access blocked, recipient notification, cleanup. CI runs it against the real stack on every push.
