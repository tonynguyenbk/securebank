# SecureBank — Implementation Plan & Status

Source spec: [`CLAUDE_CODE_SECUREBANK_MASTER_PROMPT.md`](../CLAUDE_CODE_SECUREBANK_MASTER_PROMPT.md) ·
Design: [`docs/design/DESIGN_SYSTEM.md`](design/DESIGN_SYSTEM.md)

Legend: `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked / simplified (see note)

Last updated: 2026-10-08

---

## Progress overview

| # | Phase | Status | Live-visible result after push |
|---|---|---|---|
| 0 | Repo, CI, deploy pipeline | [x] | GitHub repo + green CI badge + preview URL |
| 1 | Bootstrap (Maven, Vite, Compose infra) | [x] | Skeleton app at preview URL |
| 2 | Identity service (+ gateway) | [x] | — (API) |
| 3 | Banking core basic | [ ] | — (API) |
| 4 | Transfer engine | [ ] | — (API + tests in CI) |
| 5 | Outbox + Kafka | [ ] | — |
| 6 | Fraud service | [ ] | — |
| 7 | Audit service | [ ] | — |
| 8 | Notification service | [ ] | — |
| 9 | Frontend — customer portal | [ ] | Login, dashboard, transfer, receipt |
| 10 | Frontend — ops portal | [ ] | Fraud queue, accounts, audit |
| 11 | Dockerization (full compose) | [ ] | `docker compose up --build` works |
| 12 | Observability | [ ] | Prometheus + Grafana dashboard |
| 13 | Final docs | [ ] | README, diagrams, Postman |

---

## Decisions & environment notes

| Topic | Decision | Why |
|---|---|---|
| Repo root | `D:\SecureBank` is the monorepo root (spec's `securebank/`) | Already the workspace |
| JDK | Local JDK is 25; build with `--release 21` | Spec requires Java 21 bytecode/language level |
| Maven | Maven Wrapper (`mvnw`) committed; no global `mvn` installed | Reproducible builds, CI-friendly |
| Spring | Spring Boot 3.5.x + Spring Cloud 2025.0 (Gateway, WebFlux) | Latest 3.x line with JDK 21–25 support |
| Contracts | `docs/contracts/api.md` + `events.md`, event records in `common` | Frozen before parallel work |
| Lombok | Not used — Java `record` DTOs + constructor injection | Avoids annotation-processor issues on JDK 25; records are clearer |
| Gateway | Spring Cloud Gateway (reactive) | Compatible with Boot 3.5 |
| Postgres | One container, 5 databases (identity, banking, fraud, audit, notification); host port 5433 | Spec allows it; 5432 is taken by a local PostgreSQL 18 service on the dev machine |
| Kafka | `apache/kafka` image, KRaft single node | No ZooKeeper |
| JWT | HS256, shared secret from env `JWT_SECRET`, validated in every service | Simple, documented; RS256 listed as future improvement |
| Token storage (FE) | Access token in memory, refresh token in `sessionStorage` | Tradeoff documented in `docs/security.md` |
| Frontend libs | React 19, Vite 8, Tailwind v4, Vite, Tailwind, React Router, TanStack Query, Axios, Recharts, Lucide, `@fontsource` | Per spec |
| Deploy target | **Option A** — frontend preview on **GitHub Pages** (auto-deploy on push to `main`), mock API via MSW labelled "Preview data"; real stack via Docker Compose locally | Owner decision 2026-10-08; free; `gh` already authenticated |
| UI language | **Bilingual EN/VI** — `i18next` + `react-i18next`, toggle in header, default from browser language, persisted in `localStorage`; amounts always `vi-VN` VND format | Owner decision 2026-10-08 |

---

## Phase 0 — Repository, CI, deploy pipeline
- [x] `git init`, `.gitignore`, `.gitattributes` (LF for `*.sh`, `mvnw`)
- [x] Create GitHub repo `securebank` (account `tonynguyenbk`) and push `main` → https://github.com/tonynguyenbk/securebank
- [x] GitHub Actions `ci.yml`: backend `./mvnw verify` (Testcontainers) + frontend lint/build: backend `./mvnw verify` (Testcontainers on ubuntu runner) + frontend `npm ci && npm run build && npm run lint`
- [x] GitHub Actions `deploy-preview.yml`: build frontend with `VITE_API_MODE=mock` and publish to GitHub Pages on every push to `main` (SPA fallback via `404.html`, base `/securebank/`)
- [x] README badge + preview link → **https://tonynguyenbk.github.io/securebank/**

## Phase 1 — Bootstrap
- [x] Monorepo folders: `backend/ frontend/ infra/ docs/` (`scripts/` in Phase 11)
- [x] `backend/pom.xml` parent (Boot 3.5.16, Cloud 2025.0.3) + 6 modules + `common` (ApiError/ErrorCode, correlation filter, JWT validator + filter, outbox writer/publisher, processed-events store, event records, autoconfig)
- [x] Each service: `Application`, `application.yml`, actuator health, springdoc (audit-service smoke-run: health UP, shared Flyway migrations applied)
- [x] `frontend/`: Vite + React + TS + Tailwind with design tokens from DESIGN_SYSTEM §3–4
- [x] `docker-compose.yml` infra: postgres (+ init script for 5 DBs, host port **5433**), redis, kafka (KRaft)
- [x] `.env.example` (services also read the root `.env` via `spring.config.import`)
- [x] ✅ Check: `./mvnw -q package -DskipTests` and `npm run build` pass; common unit tests pass

## Phase 2 — Identity service
- [x] Flyway: `users`, `roles`, `user_roles`, `refresh_tokens`
- [x] Register / login / refresh / logout / me
- [x] BCrypt, JWT (uid, username, roles, jti), refresh token stored as SHA-256 hash, rotation on refresh
- [x] Logout → refresh revoke + access `jti` denylist in Redis
- [x] Login rate limit in Redis (5 attempts / 5 min per username+IP) → 429
- [x] Demo seed (profile `demo`): customer1/2, staff1, auditor1, admin1
- [x] Tests: `JwtServiceTest`, auth controller integration test (Testcontainers)

## Phase 3 — Banking core basic
- [ ] Flyway V1–V8 per spec §41
- [ ] Entities: Customer, Account (`@Version`), TransferLimit
- [ ] `/customers/me`, `/accounts`, `/accounts/{id}`, `/accounts/{id}/balance` with ownership checks
- [ ] Admin: customers/accounts list+detail, freeze/unfreeze, limits GET/PUT (`@PreAuthorize`)
- [ ] Demo seed: accounts 1000000001 (25M), 1000000002 (10M)
- [ ] Global `@ControllerAdvice` + error codes (spec §42)

## Phase 4 — Transfer engine (most important)
- [ ] `POST /transfers` following spec §10 step by step
- [ ] Idempotency: SHA-256 request hash, `UNIQUE(user_id, key)`, stored response replay, 409 on mismatch
- [ ] `PESSIMISTIC_WRITE` locks ordered by account UUID
- [ ] Per-transaction + daily limits; ACTIVE check; currency check; balance check
- [ ] Exactly 2 ledger entries (balance before/after), `TX` reference generator (DB sequence)
- [ ] Rejected transfers persisted as `REJECTED` in separate transaction (for ops visibility)
- [ ] `GET /transfers`, `GET /transfers/{id}` (filters + pagination), admin transactions, reconciliation endpoint
- [ ] Integration tests (spec §36): success, insufficient funds, frozen, per-tx limit, daily limit, duplicate key, key conflict, **concurrency (2×800k on 1M)**, **rollback after debit**, authorization, staff permissions
- [ ] Micrometer: `transfer_success_total`, `transfer_failure_total`, `transfer_latency`

## Phase 5 — Outbox + Kafka
- [ ] `outbox_events` written in transfer TX; `ACCOUNT_STATUS_CHANGED`, audit events also via outbox
- [ ] Scheduled publisher: `FOR UPDATE SKIP LOCKED` batch, publish, mark PUBLISHED, retry_count, FAILED after N
- [ ] Topics per spec §17; correlation ID in Kafka headers
- [ ] Test: publisher publishes (Kafka Testcontainer); metrics `outbox_pending_count`, `kafka_publish_failure_total`

## Phase 6 — Fraud service
- [ ] Consumer `bank.transaction.completed.v1`, `processed_events` table for idempotency
- [ ] Rules A–D (Redis sliding window for B, Redis daily sum for C, Redis set for D)
- [ ] Alerts + rule hits; risk levels; publishes `bank.fraud.alert.created.v1`
- [ ] API list/detail/review (STAFF/ADMIN review, AUDITOR read-only)
- [ ] Tests: `FraudRuleEngineTest`, consumer idempotency test

## Phase 7 — Audit service
- [ ] Consumer `bank.audit.event.v1` (+ account status / fraud review events)
- [ ] `audit_logs` append-only (no update/delete API; DB trigger blocks UPDATE/DELETE)
- [ ] `GET /audit/logs` filters (actor, action, resourceType, date range, correlationId), `GET /audit/logs/{id}`

## Phase 8 — Notification service
- [ ] Consumer transaction completed → notifications for sender & receiver (IN_APP + simulated EMAIL)
- [ ] `GET /notifications/me`; idempotent by event ID

## Phase 9 — Frontend: customer portal
- [ ] Design tokens, fonts, base components (DESIGN_SYSTEM §7)
- [ ] Auth flow (login, register, refresh interceptor, role-aware routing)
- [ ] `/dashboard`, `/accounts`, `/accounts/:id`
- [ ] `/transfer`: form → review dialog → submit with Idempotency-Key (kept on retry) → **LedgerSlip receipt**
- [ ] `/transactions` (filters, pagination), `/transactions/:id`, `/notifications`, `/profile`
- [ ] Error-code → message map; loading / empty / error states
- [ ] Playwright screenshots at 375 / 1024 / 1440 + self-critique pass

## Phase 10 — Frontend: ops portal
- [ ] OpsShell, `/ops/dashboard` (6 KPI cards from real APIs)
- [ ] `/ops/customers`, `/ops/accounts` (freeze/unfreeze, limits editor)
- [ ] `/ops/transactions` (+ ledger + reconciliation view for AUDITOR)
- [ ] `/ops/fraud`, `/ops/fraud/:id` (rules, timeline, review, freeze)
- [ ] `/ops/audit`
- [ ] AUDITOR sees no mutation controls (hidden + backend enforced)

## Phase 11 — Dockerization
- [ ] Multi-stage Dockerfiles (6 services + frontend nginx)
- [ ] Full compose with healthchecks + `depends_on: condition: service_healthy`
- [ ] `scripts/seed-demo-data.sh`, `smoke-test.sh` (runs demo scenario §51), `reset-local-env.sh`

## Phase 12 — Observability
- [ ] Actuator health/info/metrics/prometheus on all services
- [ ] Prometheus scrape config, Grafana provisioned dashboard

## Phase 13 — Docs
- [ ] README (23 sections, spec §44) · `architecture.md`, `api-flows.md`, `database-design.md`, `security.md`
- [ ] Mermaid: architecture, transfer sequence, outbox flow, ER
- [ ] `docs/api/SecureBank.postman_collection.json` + environment
- [ ] Screenshots in `docs/screenshots/`

---

## Parallel execution plan (multi-agent)

Work is split so agents never edit the same files. Shared contracts are frozen **before** fan-out.

```
Wave 0 (lead, sequential)   Phase 0 + 1 + CONTRACTS
                            ├─ backend/common  (error format, ErrorCode, correlation filter, JWT validator)
                            ├─ docs/contracts/api.md      (every endpoint, DTO, status code, role)
                            ├─ docs/contracts/events.md   (Kafka topics + JSON schemas)
                            └─ docker-compose infra, .env.example, CI
                                         │
Wave 1 (parallel, each in its own git worktree/branch)
   ┌──────────────┬──────────────────┬──────────────────────┬──────────────────────┐
   │ Agent A      │ Agent B          │ Agent C              │ Agent D              │
   │ identity-svc │ banking-core     │ fraud + audit +      │ frontend (both       │
   │ + gateway    │ (Phase 3,4,5)    │ notification (6,7,8) │ portals, 9,10) on    │
   │ (Phase 2)    │                  │                      │ MSW mocks of contract│
   └──────┬───────┴────────┬─────────┴──────────┬───────────┴──────────┬───────────┘
          └────────────────┴─────── merge to main (lead reviews, CI green) ─┘
                                         │
Wave 2 (lead)               Phase 11 Docker full stack · E2E smoke (spec §51) · Phase 12 · Phase 13
                            Frontend switched from mocks to real gateway; screenshot QA
```

| Agent | Owns (write access) | Must not touch |
|---|---|---|
| A | `backend/identity-service`, `backend/api-gateway` | `common`, contracts |
| B | `backend/banking-core-service` | other services |
| C | `backend/fraud-service`, `audit-service`, `notification-service` | banking-core |
| D | `frontend/` | `backend/` |
| Lead | `common`, `docs/contracts`, compose, CI, README, merges | — |

Contract change during Wave 1 → agent reports it, lead updates contract and notifies others.

## Deployment — chosen: Option A (GitHub Pages preview)

The full stack (6 JVM services + Postgres + Kafka + Redis) needs ~3–4 GB RAM, so free static hosting can't run it.

| Option | What you see live | Cost | Notes |
|---|---|---|---|
| **A. Frontend preview + local full stack** | UI on GitHub Pages/Vercel with a clearly-labelled *Preview data* mode (MSW mock API built from the real API contracts); full real stack via `docker compose up` locally | Free | Fastest feedback loop for UI changes on every push |
| **B. Full stack on one VPS** (e.g. Hetzner/DigitalOcean 4 GB) | Real backend + frontend, CD via GitHub Actions → SSH → `docker compose pull && up -d` | ~5–24 USD/month | Closest to "production"; needs a VPS + SSH key |
| **C. Fly.io / Railway** | Real backend (trimmed: Upstash Kafka/Redis, managed Postgres) | Paid usage | More config, multiple managed services |

---

## Change log
- 2026-10-09 — Agent A merged (identity + gateway): 68 tests green after merge (common 9, identity 43, gateway 16). Notes: BCrypt >72-byte passwords pre-hashed with SHA-256; gateway trusts only private-range proxies for X-Forwarded-For; gateway actuator route endpoint not exposed.
- 2026-10-08 — Wave 0 done: Maven multi-module + common module, API/event contracts, compose infra, CI. Wave 1 agents launched.
- 2026-10-08 — Plan and design system drafted. Parallel multi-agent plan added. Deploy = Option A (GitHub Pages). UI = bilingual EN/VI + dark mode.
- 2026-10-08 — First deploy live: login page (EN/VI, light/dark/system), build `d00d403`. Display font switched to Be Vietnam Pro (Vietnamese diacritics).
