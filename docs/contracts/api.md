# SecureBank — REST API Contract (v1)

**Status: FROZEN for Wave 1.** Agents implement exactly this. A change needs the lead to update this file first.
Events: [`events.md`](events.md). Error codes: `backend/common/.../error/ErrorCode.java`.

---

## 0. Conventions

| Topic | Rule |
|---|---|
| Entry point | Browser → API gateway `http://localhost:8080`. Frontend dev server proxies `/api` there |
| Routing (gateway) | `/api/v1/auth/**` → identity:8081 · `/api/v1/customers/**`, `/accounts/**`, `/transfers/**`, `/admin/**` → banking-core:8082 · `/api/v1/fraud/**` → fraud:8083 · `/api/v1/audit/**` → audit:8084 · `/api/v1/notifications/**` → notification:8085 |
| JSON | camelCase, `Content-Type: application/json`, unknown fields ignored |
| Money | JSON **number** with ≤ 2 decimals (Java `BigDecimal`, DB `NUMERIC(19,2)`). Currency `"VND"` only in v1 |
| Time | ISO-8601 UTC instants (`2026-10-08T03:42:00Z`). Date-only filters (`fromDate`, `toDate`) are `YYYY-MM-DD` interpreted in **Asia/Ho_Chi_Minh**; "today" and the daily limit also use that zone |
| IDs | UUID strings. Account numbers: 10-digit strings |
| Auth | `Authorization: Bearer <accessToken>` (JWT HS256, 15 min). See §1 |
| Correlation | Optional request header `X-Correlation-Id` (8–64 chars `[A-Za-z0-9._-]`); always echoed in the response |
| Pagination | Query `page` (0-based, default 0), `size` (default 20, max 100), `sort=field,asc|desc`. Response: `PageResponse` |
| Errors | Always `ApiError` (below). Never stack traces |
| Concurrency | Optimistic-lock conflicts (e.g. two staff editing the same record) → 409 `CONCURRENT_UPDATE` |

```ts
type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

type ApiError = {
  timestamp: string; status: number; code: string;   // code = ErrorCode enum name
  message: string; path: string; correlationId?: string;
  fieldErrors?: { field: string; message: string }[] // only for VALIDATION_FAILED
}
```

Roles: `CUSTOMER`, `BANK_STAFF`, `AUDITOR`, `ADMIN`. Below, **S** = BANK_STAFF, **A** = AUDITOR, **AD** = ADMIN, **C** = CUSTOMER.
AUDITOR never mutates anything. Every role check is enforced server-side (`@PreAuthorize`), the UI only hides controls.

---

## 1. Identity service (`/api/v1/auth`)

```ts
type UserResponse = {
  id: string; username: string; fullName: string; email: string; phone: string | null;
  roles: ("CUSTOMER" | "BANK_STAFF" | "AUDITOR" | "ADMIN")[]; createdAt: string
}
type TokenResponse = {
  accessToken: string; tokenType: "Bearer"; expiresIn: number;          // seconds
  refreshToken: string; refreshExpiresIn: number; user: UserResponse
}
```

| Method & path | Auth | Body → Response | Errors |
|---|---|---|---|
| `POST /auth/register` | public | `{username, password, fullName, email, phone?}` → **201** `UserResponse` | 400 `VALIDATION_FAILED`, 409 `AUTH_USERNAME_TAKEN` |
| `POST /auth/login` | public | `{username, password}` → 200 `TokenResponse` | 401 `AUTH_INVALID_CREDENTIALS`, 429 `AUTH_LOGIN_RATE_LIMITED` |
| `POST /auth/refresh` | public | `{refreshToken}` → 200 `TokenResponse` (rotated) | 401 `AUTH_REFRESH_TOKEN_INVALID` |
| `POST /auth/logout` | any | `{refreshToken}` → **204** | 401 |
| `GET /auth/me` | any | → `UserResponse` | 401 |

Rules
- Register creates a `CUSTOMER` only. Validation: `username` 3–50 `[a-z0-9._-]`; `password` 8–100 with upper, lower, digit, symbol; `fullName` 1–200; `email` valid; `phone` optional `^\+?[0-9]{9,15}$`.
  Emits `UserRegisteredEvent` (banking-core opens an empty VND account) + audit `USER_REGISTERED`.
- Passwords BCrypt (strength 12). Refresh tokens: opaque random 256-bit, stored as SHA-256 hash, **rotated** on refresh; reusing a revoked refresh token revokes all of that user's refresh tokens.
- Access JWT claims: `sub`=userId, `username`, `roles`[], `jti`, `typ`="access", `iss`="securebank-identity", `iat`, `exp`.
- Logout: revoke the refresh token + write `auth:denylist:<jti>` in Redis with TTL = remaining access-token life. Services with Redis check it.
- Login rate limit: Redis counter per `username` and per client IP, 5 failures / 5 min → 429. Successful login clears the username counter.
- Audit: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `USER_REGISTERED`.

---

## 2. Banking core — customer endpoints

```ts
type CustomerResponse = { id: string; userId: string; fullName: string; email: string; phone: string | null; createdAt: string }

type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED"
type AccountSummary = {
  id: string; accountNumber: string; type: "CURRENT"; currency: "VND";
  balance: number; status: AccountStatus; createdAt: string
}
type TransferLimits = {
  perTransactionLimit: number; dailyLimit: number;
  usedToday: number; remainingToday: number; updatedAt: string
}
type AccountDetail = AccountSummary & { customerId: string; limits: TransferLimits; updatedAt: string }

type LedgerLine = {                       // one side of the double entry
  entryType: "DEBIT" | "CREDIT";
  accountNumber: string;                  // counterparty's number is masked "******0002" for customers
  amount: number;
  balanceBefore: number | null;           // null when the line belongs to someone else's account
  balanceAfter: number | null;
  createdAt: string
}
type TransactionStatus = "PENDING" | "SUCCESS" | "FAILED" | "REJECTED"

type TransferResponse = {
  transactionId: string; transactionReference: string;   // e.g. TX202610080001
  status: TransactionStatus;
  sourceAccountNumber: string; destinationAccountNumber: string; destinationHolderName: string;
  amount: number; currency: "VND"; description: string | null;
  remainingBalance: number;                               // source balance after the transfer
  createdAt: string; completedAt: string | null;
  ledgerEntries: LedgerLine[]                             // exactly 2 when SUCCESS: DEBIT source, CREDIT destination
}

type TransactionSummary = {
  id: string; transactionReference: string;
  direction: "OUT" | "IN";               // relative to the calling customer
  sourceAccountNumber: string; destinationAccountNumber: string;
  counterpartyName: string;              // other party's full name
  amount: number; currency: "VND"; description: string | null;
  status: TransactionStatus; failureCode: string | null; createdAt: string
}
type TransactionDetail = TransactionSummary & {
  failureReason: string | null; completedAt: string | null; ledgerEntries: LedgerLine[]
}
type StatementEntry = {                   // passbook line for one own account
  id: string; transactionId: string; transactionReference: string;
  entryType: "DEBIT" | "CREDIT"; amount: number; balanceBefore: number; balanceAfter: number;
  counterpartyAccountNumber: string; counterpartyName: string; description: string | null; createdAt: string
}
```

| Method & path | Roles | Request → Response | Errors |
|---|---|---|---|
| `GET /customers/me` | C | → `CustomerResponse` | 404 `CUSTOMER_NOT_FOUND` |
| `GET /accounts` | C | → `AccountSummary[]` (own accounts) | |
| `GET /accounts/{id}` | C (owner) | → `AccountDetail` | 404 `ACCOUNT_NOT_FOUND`, 403 `ACCOUNT_NOT_OWNED` |
| `GET /accounts/{id}/balance` | C (owner) | → `{accountId, accountNumber, balance, currency, status, asOf}` | 404, 403 |
| `GET /accounts/{id}/statement` | C (owner) | `?fromDate&toDate&page&size` → `PageResponse<StatementEntry>` newest first | 404, 403 |
| `GET /accounts/lookup` | C | `?accountNumber=1000000002` → `{accountNumber, holderName, currency}` (beneficiary name check before sending) | 404 `ACCOUNT_NOT_FOUND` (also for CLOSED) |
| `POST /transfers` | C (owner of source) | header `Idempotency-Key`; body `CreateTransferRequest` → **201** `TransferResponse` | see below |
| `GET /transfers` | C | filters → `PageResponse<TransactionSummary>` | |
| `GET /transfers/{id}` | C (party) | → `TransactionDetail` | 404 `TRANSACTION_NOT_FOUND` (also when not a party — don't leak existence) |

`CreateTransferRequest = { sourceAccountNumber: string; destinationAccountNumber: string; amount: number; currency: "VND"; description?: string }`
(`amount` > 0, scale ≤ 2, `description` ≤ 255).

`GET /transfers` filters: `accountId`, `status`, `fromDate`, `toDate`, `minAmount`, `maxAmount`, `page`, `size`, `sort` (default `createdAt,desc`).
A customer sees transactions where one of their accounts is source or destination; `REJECTED` ones only as sender.

### Transfer semantics (spec §10–§14)
- `Idempotency-Key`: required, 8–100 chars `[A-Za-z0-9_-]` (the frontend sends a UUID). Scope = (userId, key).
  - same key + same payload → the **stored** status code + body are returned again, plus header `Idempotent-Replayed: true`. No money moves.
  - same key + different payload (SHA-256 of canonical request) → **409** `IDEMPOTENCY_KEY_CONFLICT`.
  - same key while the first request is still running → **409** `IDEMPOTENCY_REQUEST_IN_PROGRESS`.
  - Business rejections (422) are stored too, so a retry gets the same rejection.
- Validation order & errors:
  400 `IDEMPOTENCY_KEY_REQUIRED` · 400 `VALIDATION_FAILED` / `INVALID_TRANSFER_AMOUNT` · 400 `CURRENCY_NOT_SUPPORTED` · 400 `SAME_ACCOUNT_TRANSFER` ·
  404 `ACCOUNT_NOT_FOUND` · 403 `ACCOUNT_NOT_OWNED` · 422 `ACCOUNT_FROZEN` (source) · 422 `ACCOUNT_CLOSED` · 422 `CURRENCY_MISMATCH` ·
  422 `TRANSFER_LIMIT_EXCEEDED` · 422 `DAILY_LIMIT_EXCEEDED` · 422 `INSUFFICIENT_FUNDS`.
  409 `ACCOUNT_BUSY` when an account row lock times out (another transfer holds it); nothing is persisted, retry with the same key.
- 422 rejections after the source account is identified are persisted as a `REJECTED` transaction (no ledger lines) and emit `TransactionFailedEvent` + audit `TRANSFER_REJECTED`.
- **Frozen destination: incoming transfers are allowed** (documented policy, spec §16). Closed destination → 422 `ACCOUNT_CLOSED`.
- Demo defaults: per-transaction limit 100,000,000; daily limit 500,000,000 (sum of today's SUCCESS outgoing, Asia/Ho_Chi_Minh day).

---

## 3. Banking core — staff / admin endpoints (`/api/v1/admin`)

```ts
type CustomerAdmin = { id: string; userId: string; fullName: string; email: string; phone: string | null; accountCount: number; createdAt: string }
type AccountAdmin = {
  id: string; accountNumber: string; customerId: string; customerName: string;
  currency: "VND"; balance: number; status: AccountStatus; createdAt: string; updatedAt: string
}
type AdminTransaction = {
  id: string; transactionReference: string;
  sourceAccountId: string; sourceAccountNumber: string; sourceCustomerName: string;
  destinationAccountId: string | null; destinationAccountNumber: string; destinationCustomerName: string | null;
  amount: number; currency: "VND"; description: string | null;
  status: TransactionStatus; failureCode: string | null; failureReason: string | null;
  createdBy: string; createdAt: string; completedAt: string | null
}
type AdminLedgerEntry = {
  id: string; accountId: string; accountNumber: string; entryType: "DEBIT" | "CREDIT";
  amount: number; balanceBefore: number; balanceAfter: number; createdAt: string
}
type Reconciliation = {
  transactionId: string; transactionReference: string; status: TransactionStatus;
  entryCount: number; debitTotal: number; creditTotal: number;
  balanced: boolean;              // debitTotal == creditTotal (and entryCount == 2 for SUCCESS, 0 otherwise)
  entries: AdminLedgerEntry[]; checkedAt: string
}
type OpsStatsToday = {
  date: string;                   // YYYY-MM-DD, Asia/Ho_Chi_Minh
  transactionsToday: number; successfulToday: number; failedOrRejectedToday: number;
  totalTransferredToday: number; frozenAccounts: number; currency: "VND"
}
```

| Method & path | Roles | Request → Response | Errors / audit |
|---|---|---|---|
| `GET /admin/customers` | S A AD | `?q` (name/email/phone contains) `&page&size` → `PageResponse<CustomerAdmin>` | |
| `GET /admin/customers/{id}` | S A AD | → `CustomerAdmin & { accounts: AccountAdmin[] }` | 404 `CUSTOMER_NOT_FOUND` |
| `GET /admin/accounts` | S A AD | `?q` (account number / customer name) `&status&page&size` → `PageResponse<AccountAdmin>` | |
| `GET /admin/accounts/{id}` | S A AD | → `AccountAdmin & { limits: TransferLimits }` | 404 |
| `PATCH /admin/accounts/{id}/freeze` | S AD | `{reason}` (3–255) → `AccountAdmin` | 409 `ACCOUNT_STATUS_UNCHANGED`, 422 `ACCOUNT_CLOSED` · audit `ACCOUNT_FREEZE` · event `AccountStatusChanged` |
| `PATCH /admin/accounts/{id}/unfreeze` | S AD | `{reason}` → `AccountAdmin` | same · audit `ACCOUNT_UNFREEZE` |
| `GET /admin/accounts/{id}/limits` | S A AD | → `TransferLimits & { accountId }` | 404 |
| `PUT /admin/accounts/{id}/limits` | S AD | `{perTransactionLimit, dailyLimit}` (> 0, per ≤ daily) → same | 400 · audit `TRANSFER_LIMIT_UPDATE` (before/after) |
| `GET /admin/transactions` | S A AD | `?status&accountNumber&reference&fromDate&toDate&minAmount&maxAmount&page&size&sort` → `PageResponse<AdminTransaction>` | |
| `GET /admin/transactions/{id}` | S A AD | → `AdminTransaction & { ledgerEntries: AdminLedgerEntry[] }` | 404 `TRANSACTION_NOT_FOUND` |
| `GET /admin/reconciliation/transactions/{id}` | A AD | → `Reconciliation` | 404 |
| `GET /admin/stats/today` | S A AD | → `OpsStatsToday` | |
| `GET /admin/stats/daily` | S A AD | `?days=14` (1–90) → `{ date: string; count: number; amount: number }[]` SUCCESS only, oldest first | |

CUSTOMER calling any `/admin/**` → 403 `FORBIDDEN_OPERATION`. AUDITOR calling a mutation → 403.

---

## 4. Fraud service (`/api/v1/fraud`)

```ts
type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"       // 0–29 / 30–59 / 60–79 / 80+
type FraudAlertStatus = "OPEN" | "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "CLOSED"
type FraudAlertSummary = {
  id: string; transactionId: string; transactionReference: string;
  customerId: string; customerName: string; sourceAccountId: string; sourceAccountNumber: string;
  amount: number; currency: "VND"; riskScore: number; riskLevel: RiskLevel;
  status: FraudAlertStatus; createdAt: string
}
type FraudAlertDetail = FraudAlertSummary & {
  destinationAccountNumber: string; destinationCustomerName: string; transactionOccurredAt: string;
  rules: { ruleCode: "HIGH_AMOUNT" | "HIGH_FREQUENCY" | "DAILY_VELOCITY" | "NEW_BENEFICIARY";
           description: string; scoreContribution: number; details: string }[];
  reviewedBy: string | null; reviewedByUsername: string | null; reviewedAt: string | null; reviewNote: string | null;
  timeline: { at: string; status: FraudAlertStatus; actorUsername: string | null; note: string | null }[]
}
```

| Method & path | Roles | Request → Response | Errors |
|---|---|---|---|
| `GET /fraud/alerts` | S A AD | `?status&riskLevel&customerId&page&size&sort` (default `createdAt,desc`) → `PageResponse<FraudAlertSummary>` | |
| `GET /fraud/alerts/{id}` | S A AD | → `FraudAlertDetail` | 404 `FRAUD_ALERT_NOT_FOUND` |
| `PATCH /fraud/alerts/{id}/review` | S AD | `{status, note?}` → `FraudAlertDetail` | 409 `FRAUD_ALERT_INVALID_TRANSITION`, 400 · audit `FRAUD_ALERT_REVIEW` |
| `GET /fraud/alerts/stats` | S A AD | → `{open, underReview, critical, high, createdToday}` | |

- Rules (spec §15), evaluated on each `TransactionCompletedEvent`:
  `HIGH_AMOUNT` amount ≥ 100,000,000 → +40 · `HIGH_FREQUENCY` > 5 outgoing transfers from the customer in 60 s (Redis sorted-set window) → +30 ·
  `DAILY_VELOCITY` customer's outgoing total today > 200,000,000 (Redis, configurable) → +20 ·
  `NEW_BENEFICIARY` first transfer source-customer → destination account (Redis set + DB) **and** amount ≥ 10,000,000 → +20.
- An alert is created when score ≥ 30 (MEDIUM+). Emits `FraudAlertCreatedEvent` + audit `FRAUD_ALERT_CREATED`.
- Review transitions: `OPEN → UNDER_REVIEW | APPROVED | REJECTED | CLOSED`; `UNDER_REVIEW → APPROVED | REJECTED | CLOSED`; `APPROVED | REJECTED → CLOSED`; `CLOSED` is terminal.
  `note` required (1–1000) for APPROVED, REJECTED, CLOSED. APPROVED = legitimate; REJECTED = confirmed fraud.
- "Freeze account" on the alert page calls banking-core `PATCH /admin/accounts/{sourceAccountId}/freeze`.

---

## 5. Audit service (`/api/v1/audit`)

```ts
type AuditLog = {
  id: string; eventId: string; occurredAt: string; receivedAt: string;
  actorUserId: string | null; actorUsername: string | null; actorRole: string | null;
  action: string; resourceType: string; resourceId: string | null;
  outcome: "SUCCESS" | "FAILURE"; correlationId: string | null; sourceService: string;
  ipAddress: string | null                                      // omitted for BANK_STAFF
}
type AuditLogDetail = AuditLog & { before: Record<string, unknown> | null; after: Record<string, unknown> | null }
```

| Method & path | Roles | Request → Response |
|---|---|---|
| `GET /audit/logs` | A AD (all) · S (restricted) | `?actor` (username contains) `&actorUserId&action&resourceType&resourceId&from&to` (instants) `&correlationId&page&size` → `PageResponse<AuditLog>` newest first |
| `GET /audit/logs/{id}` | A AD · S (restricted) | → `AuditLogDetail` · 404 `AUDIT_LOG_NOT_FOUND` |
| `GET /audit/logs/actions` | A AD S | → `string[]` distinct actions (for the filter dropdown) |

BANK_STAFF restriction: only `resourceType` in `ACCOUNT`, `TRANSACTION`, `FRAUD_ALERT`; `ipAddress` omitted.
Audit records are append-only: no update/delete endpoints; a DB trigger rejects `UPDATE`/`DELETE` on `audit_logs`.

Actions: `USER_REGISTERED`, `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `ACCOUNT_OPENED`, `TRANSFER_COMPLETED`, `TRANSFER_REJECTED`,
`ACCOUNT_FREEZE`, `ACCOUNT_UNFREEZE`, `TRANSFER_LIMIT_UPDATE`, `FRAUD_ALERT_CREATED`, `FRAUD_ALERT_REVIEW`.
Resource types: `USER`, `ACCOUNT`, `TRANSACTION`, `FRAUD_ALERT`.

---

## 6. Notification service (`/api/v1/notifications`)

```ts
type Notification = {
  id: string; channel: "EMAIL" | "SMS" | "IN_APP"; status: "PENDING" | "SENT" | "FAILED";
  templateCode: "TRANSFER_SENT" | "TRANSFER_RECEIVED" | "TRANSFER_REJECTED" | "ACCOUNT_FROZEN" | "ACCOUNT_UNFROZEN" | "WELCOME";
  params: Record<string, string | number>;   // e.g. {amount, currency, reference, counterpartyName, accountNumber, balanceAfter}
  subject: string; message: string;            // English rendering (used for simulated email/SMS + logs)
  read: boolean; relatedTransactionId: string | null; createdAt: string; sentAt: string | null
}
```

| Method & path | Roles | Request → Response |
|---|---|---|
| `GET /notifications/me` | any | `?channel&unreadOnly&page&size` → `PageResponse<Notification>` newest first (recipient = JWT `sub`) |
| `GET /notifications/me/unread-count` | any | → `{count}` (IN_APP only) |
| `PATCH /notifications/{id}/read` | owner | → **204** · 404 `NOTIFICATION_NOT_FOUND` (also when not owner) |

The frontend renders `templateCode` + `params` through i18n (EN/VI); `subject`/`message` are the English fallback.

Exact `params` per template (account numbers are always masked `******0002`; null values are omitted):

| templateCode | params |
|---|---|
| `TRANSFER_SENT` | `amount, currency, reference, accountNumber, counterpartyName, counterpartyAccountNumber, balanceAfter` |
| `TRANSFER_RECEIVED` | `amount, currency, reference, accountNumber, counterpartyName, counterpartyAccountNumber, balanceAfter` |
| `TRANSFER_REJECTED` | `amount, currency, reference, accountNumber, counterpartyAccountNumber, failureCode` (an `ErrorCode` name — render via the error map; **no counterpartyName**) |
| `ACCOUNT_FROZEN`, `ACCOUNT_UNFROZEN` | `accountNumber, status` |
| `WELCOME` | `fullName, username` |
Created on: `TransactionCompleted` (sender: TRANSFER_SENT IN_APP + EMAIL; recipient: TRANSFER_RECEIVED IN_APP + SMS),
`TransactionFailed` (sender: TRANSFER_REJECTED IN_APP), `AccountStatusChanged` (owner: ACCOUNT_FROZEN/UNFROZEN IN_APP + EMAIL),
`UserRegistered` (WELCOME IN_APP). Fraud alerts are **not** notified to customers (no tipping-off).

---

## 7. Demo seed (`SECUREBANK_DEMO_SEED=true`, local only)

Fixed UUIDs so identity and banking-core seed independently and still match.

| Username / password | Role | userId | Full name | customerId | Account (id) | Balance |
|---|---|---|---|---|---|---|
| customer1 / Customer@123 | CUSTOMER | `00000000-0000-4000-8000-000000000101` | Nguyễn Văn An | `00000000-0000-4000-8000-000000001101` | 1000000001 (`…000000002101`) | 25,000,000 |
| customer2 / Customer@123 | CUSTOMER | `00000000-0000-4000-8000-000000000102` | Trần Thị Bình | `00000000-0000-4000-8000-000000001102` | 1000000002 (`…000000002102`) | 10,000,000 |
| staff1 / Staff@123 | BANK_STAFF | `00000000-0000-4000-8000-000000000201` | Lê Minh Châu | — | — | — |
| auditor1 / Auditor@123 | AUDITOR | `00000000-0000-4000-8000-000000000301` | Phạm Quốc Dũng | — | — | — |
| admin1 / Admin@123 | ADMIN | `00000000-0000-4000-8000-000000000401` | Hoàng Thu Hà | — | — | — |

Account ids: `00000000-0000-4000-8000-000000002101` and `…-000000002102`. Emails `<username>@demo.securebank.local`.
Seeding is idempotent (insert-if-absent) and never runs unless the flag is true. Opening balances are seeded directly
(documented: the ledger records movements from transfers onward). New account numbers come from a DB sequence starting at 1000000101.

---

## 8. Ports & service names

| Service | Port | Database | Swagger UI |
|---|---|---|---|
| api-gateway | 8080 | — | — |
| identity-service | 8081 | identity | `:8081/swagger-ui.html` |
| banking-core-service | 8082 | banking | `:8082/swagger-ui.html` |
| fraud-service | 8083 | fraud | `:8083/swagger-ui.html` |
| audit-service | 8084 | audit | `:8084/swagger-ui.html` |
| notification-service | 8085 | notification | `:8085/swagger-ui.html` |
| frontend (dev / docker) | 3000 | — | — |
