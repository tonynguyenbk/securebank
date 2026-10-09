# Security model

## Authentication

| Item | Implementation |
|---|---|
| Passwords | BCrypt, strength 12. Passwords over 72 UTF-8 bytes (possible with Vietnamese text) are pre-hashed with SHA-256 so BCrypt never truncates them. Unknown usernames still run a BCrypt comparison so response time doesn't reveal which accounts exist |
| Access token | JWT HS256, 15 minutes. Claims: `sub` (user ID), `username`, `roles`, `jti`, `typ=access`, `iss`. Every service verifies signature, issuer, expiry and type |
| Refresh token | 256-bit random opaque value, stored only as SHA-256, 7 days, **rotated on every use**; presenting a revoked token revokes every refresh token of that user (theft detection) |
| Logout | refresh token revoked; access token `jti` denylisted in Redis for its remaining lifetime. The revocation check fails closed if Redis is unreachable |
| Login abuse | 5 failed attempts per username and per IP in 5 minutes → `429 AUTH_LOGIN_RATE_LIMITED` (atomic Redis INCR+EXPIRE). Gateway rate limits login and transfer requests per client IP |
| Secrets | `JWT_SECRET`, database and Grafana passwords come from environment variables; `.env` is git-ignored and `.env.example` holds placeholders only |

## Authorization

| Role | Can | Cannot |
|---|---|---|
| CUSTOMER | own profile, own accounts, own transactions, transfer from own accounts, own notifications | any `/admin/**`, fraud or audit endpoint |
| BANK_STAFF | search customers/accounts/transactions, freeze/unfreeze, edit limits, review fraud alerts, read restricted audit logs | reconciliation, full audit log |
| AUDITOR | read transactions, ledger, reconciliation, full audit log, fraud alerts | **any mutation** (403 everywhere) |
| ADMIN | everything above | — |

- Enforced server-side with `@PreAuthorize` on every endpoint plus URL rules as a second layer; the UI only hides controls.
- **Ownership comes from the token**, never from the request body or path: the service maps `sub` → customer and checks the account
  belongs to it. Values the client could tamper with — user ID, roles, status, balances, ledger values — are always derived on the
  server.
- A customer asking for someone else's transaction gets `404`, not `403`, so IDs can't be probed for existence.
- BANK_STAFF audit access is restricted in the query itself (only ACCOUNT / TRANSACTION / FRAUD_ALERT records, no IP addresses).

## Auditability

Audit events are produced in the same transaction as the action (via the outbox) for: registration, login success/failure (including
rate-limited attempts), logout, account opened, transfer completed/rejected, freeze/unfreeze, limit change, fraud alert created/
reviewed. Each records actor ID/username/role, action, resource, before/after state, outcome, IP address and correlation ID. The
`audit_logs` and `ledger_entries` tables reject UPDATE, DELETE and TRUNCATE through triggers.

## Transport & web hardening

- CORS is configured once, at the gateway (explicit origins, methods and headers; no credentials).
- Gateway trusts `X-Forwarded-For` only from private-range proxies, so clients can't spoof the IP used for rate limits and audit.
- The gateway's actuator route-management endpoint is not exposed.
- Responses carry `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`; errors never contain
  stack traces (`server.error.include-stacktrace: never` + a global exception handler).
- Correlation IDs from clients are accepted only if they match `[A-Za-z0-9._-]{8,64}` (no log injection).

## Data handling

- Account numbers are masked (`******0001`) in logs, notifications and in the counterparty lines customers see.
- Passwords, JWTs and refresh tokens are never logged; request DTOs override `toString` for secret fields.
- Fraud alerts are not shown or notified to customers, and freeze reasons are not forwarded to them (no tipping-off).

## Frontend token storage — trade-off

The access token lives in memory only. The refresh token is kept in `sessionStorage` so a page reload doesn't sign the user out; it
is cleared when the tab closes. This is readable by JavaScript, so an XSS bug could steal it. The production choice is an
`HttpOnly; Secure; SameSite=Strict` cookie scoped to the refresh endpoint, set by the gateway — listed as a follow-up. Mitigations in
place: short access-token lifetime, refresh rotation with reuse detection, no third-party scripts, React's escaping by default.

## Known limitations

- HS256 shared secret: every service holding the secret could mint tokens. RS256 + JWKS is the next step.
- No MFA / step-up authentication for high-value transfers (future work).
- Demo credentials exist only when `SECUREBANK_DEMO_SEED=true`; never enable it outside local demos.
