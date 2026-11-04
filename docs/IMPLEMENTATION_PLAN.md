# PayFlow Enterprise Implementation Plan

Primary Source of Truth: **PayFlow Enterprise AI IDE Implementation Specification (Version 2.0)**

---

## 1. Architectural Decisions & Standards

- **Backend Architecture**: Modular monolith using Spring Boot 3.4.2, Java 21 LTS, Spring Data JPA, Spring Security, Flyway, Resilience4j, Kafka / In-Memory Outbox fallback for standalone testing, Redis / Caffeine cache fallback.
- **Package Layout**: `com.payflow`
  - `common`: Error codes, exceptions, global exception handler, correlation ID filter, clock, money types, pagination.
  - `identity`: User, roles (`PLATFORM_ADMIN`, `MERCHANT_ADMIN`, `MERCHANT_OPERATOR`, `READ_ONLY_ANALYST`), BCrypt password hashing, JWT / session auth, API key hashing (`SHA-256` with high entropy secret shown once).
  - `merchant`: Tenant context, merchant profile, tenant isolation enforcement.
  - `payment`: Payment aggregate, state transitions, attempts, execution pipeline.
  - `routing`: Routing policies (priority-based, weighted routing, currency/method filtering, circuit breaker exclusion).
  - `provider`: `PaymentProviderPort`, `StripeSimulatorAdapter`, `AdyenSimulatorAdapter`, webhook signature verifiers, normalized statuses/errors.
  - `idempotency`: Distributed idempotency manager, canonical request hashing (`SHA-256`), concurrent insert race handling, cached responses.
  - `webhook`: Webhook inbox, deduplication (`provider_id`, `provider_event_id`), asynchronous event processing, replay endpoint.
  - `refund`: Refund aggregate, refundable balance invariant (`sum(refunds) <= captured_amount`), provider refund dispatch.
  - `reconciliation`: Stale attempt scanner (`UNKNOWN` / `PENDING` attempts), provider status lookup, discrepancy resolution workflow.
  - `outbox`: Transactional outbox table (`outbox_events`), scheduled publisher, at-least-once delivery, Kafka producer.
  - `audit`: Append-only audit logging with sensitive data redaction.
  - `reporting`: Analytics overview DTOs, time-window aggregations, provider performance metrics.
- **Database & Persistence**:
  - PostgreSQL 16+ dialect.
  - H2 in PostgreSQL compatibility mode for unit/fast test suites without Docker daemon dependency; Testcontainers PostgreSQL for containerized integration tests.
  - Explicit integer minor units (`amount_minor BIGINT`) + 3-letter ISO currency code (`currency CHAR(3)`).
  - Optimistic locking `@Version` on mutable aggregates.
  - Flyway migrations for schema evolution.
- **Frontend Architecture**:
  - React 18 + TypeScript + Vite + Tailwind CSS + Lucide React + TanStack Query + React Router v6.
  - Strict tenant scoping, secure token handling, feedback toasts, server-side pagination, empty/loading/error states.

---

## 2. Phase-by-Phase Execution Tracker

### Phase 0: Repository Discovery & Planning
- [x] Inspect existing repository structure and build tools.
- [x] Configure Maven wrapper and Java 21 toolchain.
- [x] Establish `docs/IMPLEMENTATION_PLAN.md` and architecture decisions.

### Phase 1: Foundation & Infrastructure
- [x] Set up directory structure: `backend/`, `frontend/`, `infrastructure/`, `docs/`.
- [x] Configure `backend/pom.xml` with dependencies:
  - Spring Boot 3.3.4 (Web, Security, Data JPA, Validation, Actuator)
  - PostgreSQL Driver, Flyway Core & Flyway Database PostgreSQL
  - Redis / Spring Data Redis
  - Spring Kafka
  - Resilience4j (CircuitBreaker, Retry, Bulkhead)
  - JJWT (JSON Web Token)
  - Springdoc OpenAPI (Swagger UI)
  - Testcontainers & JUnit 5
- [x] Set up root Docker Compose (`infrastructure/docker-compose.yml`) with PostgreSQL, Redis, Kafka, Zookeeper/KRaft, Prometheus, Grafana.
- [x] Set up `.env.example` with documented environment configurations.
- [x] Create Flyway baseline migration `V1__init_payflow_schema.sql` implementing all required tables, constraints, check constraints, and indexes.
- [x] Implement central error envelope, error codes, and correlation ID MDC filter.
- [x] Health check and actuator endpoints verified.

### Phase 2: Identity, Tenancy & Security
- [x] Implement `User`, `Merchant`, `MerchantUser`, `ApiCredential` domain entities and repositories.
- [x] Implement password hashing (BCrypt) and API Key generation/hashing.
- [x] Implement `TenantContext` and security filter:
  - Bearer token (JWT) or API Key authentication.
  - Role-based authorization (`PLATFORM_ADMIN`, `MERCHANT_ADMIN`, `MERCHANT_OPERATOR`, `READ_ONLY_ANALYST`).
  - Strict tenant isolation: verify Merchant A cannot read/mutate Merchant B resources.
- [x] Endpoints:
  - `POST /api/v1/auth/login`
  - `GET /api/v1/auth/me`
  - `POST /api/v1/api-keys`
  - `GET /api/v1/api-keys`
  - `DELETE /api/v1/api-keys/{id}`
- [x] Write cross-tenant security integration tests.

### Phase 3: Core Payments & Lifecycle
- [x] Implement `Payment` and `PaymentAttempt` entities with optimistic locking.
- [x] Implement Payment State Machine with strict transition validation:
  - `CREATED` -> `PROCESSING` -> `SUCCEEDED` / `FAILED` / `PENDING` / `REQUIRES_ACTION` / `CANCELLED`
  - Terminal state regression protection.
- [x] Endpoints:
  - `POST /api/v1/payments` (with required `Idempotency-Key`)
  - `GET /api/v1/payments/{paymentId}`
  - `GET /api/v1/payments` (server-side pagination, filters)
  - `GET /api/v1/payments/{paymentId}/attempts`
- [x] Write payment lifecycle unit and integration tests.

### Phase 4: Provider Abstraction & Routing
- [x] Implement `PaymentProviderPort` interface:
  - `capabilities()`
  - `initiatePayment(PaymentCommand)`
  - `getPaymentStatus(providerReference)`
  - `refund(RefundCommand)`
  - `verifyWebhook(headers, rawBody)`
  - `parseWebhook(rawBody)`
- [x] Implement Provider A: `StripeSimulatorAdapter` (supports Card, 3DS, Success, Decline, Timeout, Webhooks).
- [x] Implement Provider B: `AdyenSimulatorAdapter` (supports UPI, Card, Netbanking, Success, Decline, Timeout, Webhooks).
- [x] Implement Routing Engine:
  - Filtering by currency, payment method, amount limits, provider enablement.
  - Exclusion of open circuit breakers.
  - Priority routing & weighted routing algorithms.
  - Route decision explanation persistence.
- [x] Endpoints:
  - `GET /api/v1/providers`
  - `PATCH /api/v1/providers/{providerId}/configuration`
  - `GET/POST/PUT /api/v1/routing-rules`
- [x] Write provider contract and routing tests.

### Phase 5: Idempotency & Resilience
- [x] Implement distributed Idempotency Manager:
  - Key format: `merchant_id:operation:key`
  - Canonical request hashing (SHA-256 of normalized body)
  - Concurrent insert race handling (catch unique constraint violation, reread committed record)
  - 409 Conflict if payload differs for same key
  - Replay of saved response for matching payload
- [x] Configure Resilience4j circuit breakers, bulkheads, and timeouts for external provider calls.
- [x] Handle ambiguous outcomes (timeouts after transmission marked `UNKNOWN` / `PENDING`, never blindly retry or double charge).
- [x] Write concurrency and resilience tests.

### Phase 6: Webhooks Durable Inbox & Deduplication
- [x] Implement Webhook Inbox table `webhook_events` with unique constraint `(provider_id, provider_event_id)`.
- [x] Raw request signature verification before trusting webhook data.
- [x] Asynchronous event processor updating payment attempts and payment aggregates.
- [x] Duplicate webhook handling: return 200 OK immediately without re-executing state side effects.
- [x] Endpoint: `POST /api/v1/webhooks/{providerCode}`.
- [x] Webhook replay endpoint for operators: `POST /api/v1/webhooks/{id}/replay`.
- [x] Write webhook signature, deduplication, and out-of-order tests.

### Phase 7: Refunds Management
- [x] Implement `Refund` aggregate and repository.
- [x] Refund validation:
  - Payment must be `SUCCEEDED` or `PARTIALLY_REFUNDED`.
  - Amount must be > 0.
  - Sum of successful and pending refunds must not exceed captured amount.
- [x] Dispatch refund through provider adapter.
- [x] Endpoints:
  - `POST /api/v1/payments/{paymentId}/refunds` (with `Idempotency-Key`)
  - `GET /api/v1/refunds/{refundId}`
  - `GET /api/v1/refunds`
- [x] Write refund calculation and race condition tests.

### Phase 8: Transactional Outbox, Events & Reconciliation
- [x] Implement `outbox_events` atomic emission in same DB transaction as payment/refund state changes.
- [x] Outbox publisher worker polling unpublished events, publishing to Kafka, handling broker downtime and exponential backoff retry.
- [x] Stale attempt scanner (`UNKNOWN` / `PENDING` attempts exceeding threshold) creating `reconciliation_cases`.
- [x] Discrepancy resolution service with audit justification.
- [x] Endpoints:
  - `GET /api/v1/reconciliation-cases`
  - `POST /api/v1/reconciliation-cases/{id}/resolve`
- [x] Write outbox recovery and reconciliation tests.

### Phase 9: Reporting, Audit & Operations
- [x] Reporting API: `GET /api/v1/analytics/overview` (KPIs, volume, success rate, provider comparison).
- [x] Audit logs API: `GET /api/v1/audit-logs` (actor, action, resource, redacted metadata).
- [x] Micrometer metrics & Prometheus metrics exporter.
- [x] Structured JSON logging with correlation IDs.

### Phase 10: Frontend Console Implementation
- [x] Set up React + Vite + Tailwind CSS application in `frontend/`.
- [x] Components: Navbar, Sidebar, Page layout, Status badges, Modal dialogs, Toast notifications.
- [x] Pages:
  - Sign In (`/login`)
  - Overview / Dashboard (`/`)
  - Payments / Transactions list & search (`/payments`)
  - Payment Detail & Attempt Timeline (`/payments/:id`)
  - Providers Management (`/providers`)
  - Routing Rules Editor (`/routing`)
  - Refunds (`/refunds`)
  - Reconciliation Cases (`/reconciliation`)
  - Audit Logs (`/audit`)
  - Merchant Settings & API Keys (`/settings`)
- [x] Interactive payment simulator modal for testing success, decline, timeout, 3DS scenarios.
- [x] Frontend unit tests (Vitest) & E2E smoke verification.

### Phase 11: Verification, Documentation & CI/CD
- [x] Full backend test suite execution (Unit, Repository, Integration, Security).
- [x] Frontend production build & lint checks.
- [x] Comprehensive documentation:
  - `README.md`
  - `docs/architecture.md`
  - `docs/database.md`
  - `docs/api.md`
  - `docs/payment-state-machine.md`
  - `docs/provider-adapters.md`
  - `docs/security.md`
  - `docs/runbooks.md`
  - `docs/decisions/` ADRs
- [x] Final verification against all 15 acceptance criteria (AC-01 through AC-15).

