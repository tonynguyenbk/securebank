# SecureBank — Digital Banking Transaction & Fraud Monitoring Platform

[![Deploy frontend preview](https://github.com/tonynguyenbk/securebank/actions/workflows/deploy-preview.yml/badge.svg)](https://github.com/tonynguyenbk/securebank/actions/workflows/deploy-preview.yml)

**Live UI preview:** https://tonynguyenbk.github.io/securebank/ (frontend only, preview data — the backend runs locally via Docker Compose)

A production-inspired banking platform focused on transaction correctness: ACID transfers, pessimistic locking,
idempotent requests, a double-entry ledger, transactional outbox + Kafka, rule-based fraud detection, and a full audit trail.

> 🚧 Work in progress. Build plan and live status: [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) ·
> Design system: [`docs/design/DESIGN_SYSTEM.md`](docs/design/DESIGN_SYSTEM.md)

The full README (architecture, transfer lifecycle, security model, setup) is written in Phase 13.
