# FluxPay Database Schema & Migration Guide

## 1. Schema Evolution & Flyway

All database schema changes are managed via Flyway migrations under `backend/src/main/resources/db/migration/`:
- `V1__init_payflow_schema.sql`: Initial 15 tables, foreign keys, unique constraints, and operational indexes.
- `V2__seed_providers.sql`: Seed data for platform providers (`STRIPE`, `ADYEN`).

---

## 2. Core Tables Overview

| Table Name | Primary Key | Description |
|---|---|---|
| `merchants` | UUID | Tenant organizations with currency and status |
| `users` | UUID | User authentication accounts with BCrypt password hashes |
| `merchant_users` | UUID | Multi-tenant user mapping and roles (`MERCHANT_ADMIN`, etc.) |
| `api_credentials` | UUID | SHA-256 hashed merchant API keys (`pf_live_...`) |
| `providers` | UUID | Payment gateway catalog (`STRIPE`, `ADYEN`) |
| `merchant_provider_configs`| UUID | Per-merchant gateway configurations (priority, weight, enabled) |
| `routing_rules` | UUID | Merchant custom routing policies (`PRIORITY`, `WEIGHTED`) |
| `payments` | UUID | Main payment aggregate with optimistic locking `@Version` |
| `payment_attempts` | UUID | Gateway execution attempts with provider references and status |
| `idempotency_records` | UUID | Distributed idempotency cache with request hashes and safe responses |
| `webhook_events` | UUID | Durable webhook inbox with unique `(provider_id, provider_event_id)` |
| `refunds` | UUID | Refund transactions with amount minor and reason |
| `outbox_events` | UUID | Transactional outbox events for guaranteed at-least-once publishing |
| `reconciliation_cases`| UUID | Discrepancy cases for stale `UNKNOWN`/`PENDING` attempts |
| `audit_logs` | UUID | Append-only audit trail with sanitized metadata |

---

## 3. Operational Indexes

Performance indexes created in `V1__init_payflow_schema.sql`:
- `idx_payments_merchant_created`: `(merchant_id, created_at)` for date-range pagination.
- `idx_payments_merchant_status_created`: `(merchant_id, status, created_at)` for status filtering.
- `idx_payments_order`: `(merchant_id, order_id)` for merchant order lookups.
- `idx_payment_attempts_payment`: `(payment_id)` for attempt lookups.
- `idx_payment_attempts_provider_ref`: `(provider_reference)` for incoming webhook matching.
- `idx_refunds_payment_status`: `(payment_id, status)` for active refund balance summation.
- `idx_outbox_unpublished`: `(published_at, occurred_at)` for outbox worker polling.
- `idx_reconciliation_status`: `(case_status, created_at)` for open discrepancy queries.
- `idx_audit_logs_merchant_created`: `(merchant_id, created_at)` for audit log streams.

