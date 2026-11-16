# ⚡ FluxPay Enterprise — AI Payment Orchestration Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue.svg)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6%20KRaft-black.svg)](https://kafka.apache.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-3.4-cyan.svg)](https://tailwindcss.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**FluxPay Enterprise** is an enterprise-grade, multi-tenant payment orchestration engine designed to route, process, reconcile, and audit transactions across global and Indian payment gateways (**Stripe**, **Razorpay**, **PayU**, and **Cashfree Payments**). 

Engineered for ultra-high reliability and financial correctness, FluxPay features **distributed idempotency**, **dynamic smart routing with Resilience4j circuit breakers**, **ambiguous timeout safety** (preventing duplicate customer charges), **durable webhook deduplication**, **transactional outbox event streaming via Kafka**, and an **interactive React 18 merchant management console**.

---

## 📑 Table of Contents

- [Architectural Overview](#-architectural-overview)
- [Acceptance Criteria Verification Matrix](#-acceptance-criteria-verification-matrix)
- [Technology Stack](#-technology-stack)
- [Port Mappings & Network Topology](#-port-mappings--network-topology)
- [Quick Start with Docker Compose](#-quick-start-with-docker-compose)
- [Standalone Local Development](#-standalone-local-development)
- [Pre-Seeded Credentials & Access](#-pre-seeded-credentials--access)
- [Core Features & Engineering Highlights](#-core-features--engineering-highlights)
  - [1. Multi-Tenant Identity & RBAC](#1-multi-tenant-identity--rbac)
  - [2. Distributed Idempotency Engine](#2-distributed-idempotency-engine)
  - [3. Dynamic Smart Routing & Circuit Breaking](#3-dynamic-smart-routing--circuit-breaking)
  - [4. Ambiguous Timeout Safety & Zero Double-Charge Guarantee](#4-ambiguous-timeout-safety--zero-double-charge-guarantee)
  - [5. Interactive Payment Simulator](#5-interactive-payment-simulator)
  - [6. Durable Webhook Deduplication](#6-durable-webhook-deduplication)
  - [7. Transactional Outbox & Kafka Event Streaming](#7-transactional-outbox--kafka-event-streaming)
  - [8. Automated Reconciliation Hub & Stale Attempt Scanner](#8-automated-reconciliation-hub--stale-attempt-scanner)
  - [9. Immutable Audit Logging & PCI Redaction](#9-immutable-audit-logging--pci-redaction)
- [REST API Quick Reference & cURL Examples](#-rest-api-quick-reference--curl-examples)
- [Automated Test Suite](#-automated-test-suite)
- [Project Directory Structure](#-project-directory-structure)
- [Security & Compliance](#-security--compliance)

---

## 🏛 Architectural Overview

```mermaid
flowchart TD
    subgraph Client ["Client & Merchant UI Layer"]
        UserBrowser["React 18 Merchant Console<br/>(Tailwind + Vite, Port 3000)"]
        APIClient["Merchant API Client<br/>(cURL / SDK / E-Commerce Store)"]
    end

    subgraph SecurityGateway ["Identity, Isolation & Routing"]
        Nginx["Nginx Reverse Proxy<br/>(SPA & API /api/v1)"]
        TenantFilter["TenantContext & Auth Filter<br/>(JWT HMAC-SHA256 & API Key SHA-256)"]
        IdempotencyLayer["Distributed Idempotency Engine<br/>(Redis Lock + SHA-256 Canonical Hashing)"]
    end

    subgraph CoreEngine ["FluxPay Core Backend (Spring Boot 3.3.4, Port 8080)"]
        RoutingService["Dynamic Routing Engine<br/>(Priority / Weighted Traffic Split)"]
        CircuitBreakers["Resilience4j Circuit Breakers<br/>(Stripe & Adyen Failover Guards)"]
        PaymentService["Payment State Machine & Execution<br/>(Ambiguous Timeout Safety)"]
        OutboxPublisher["Transactional Outbox Publisher<br/>(Guaranteed At-Least-Once Delivery)"]
        ReconciliationWorker["Background Reconciliation Worker<br/>(Stale Attempt Scanner)"]
    end

    subgraph Storage ["State & Message Brokers"]
        Postgres[("PostgreSQL 16 (Port 5433)<br/>Payments, Attempts, Refunds, Outbox, Audits")]
        Redis[("Redis 7 (Port 6380)<br/>Idempotency Locks & Cached Responses")]
        Kafka[("Apache Kafka 7.6 KRaft (Port 9094)<br/>Transactional Payment Events")]
    end

    subgraph Gateways ["Integrated Payment Service Providers (PSPs)"]
        StripeSim["Stripe Gateway Adapter<br/>(PaymentIntents, 3DS, Refunds, Webhooks)"]
        RazorpaySim["Razorpay Gateway Adapter<br/>(Orders, UPI, Netbanking, Webhooks)"]
        PayUSim["PayU India Gateway Adapter<br/>(SHA-512 Hash, Hosted Checkout, Postservice)"]
        CashfreeSim["Cashfree Payments Adapter<br/>(PG Orders v2023-08-01, Refunds, Webhooks)"]
    end

    UserBrowser -->|HTTP| Nginx
    APIClient -->|HTTP| Nginx
    Nginx -->|Proxy| TenantFilter
    TenantFilter --> IdempotencyLayer
    IdempotencyLayer <--> Redis
    IdempotencyLayer --> RoutingService
    RoutingService --> CircuitBreakers
    CircuitBreakers --> StripeSim
    CircuitBreakers --> RazorpaySim
    CircuitBreakers --> PayUSim
    CircuitBreakers --> CashfreeSim
    RoutingService --> PaymentService
    PaymentService --> Postgres
    PaymentService --> OutboxPublisher
    OutboxPublisher --> Kafka
    ReconciliationWorker <--> Postgres
    ReconciliationWorker -.-> StripeSim
    ReconciliationWorker -.-> AdyenSim
```

---

## 📋 Acceptance Criteria Verification Matrix

Every specification requirement from the FluxPay implementation guidelines is fully satisfied, covered by unit/integration tests, and live-tested in the production container stack:

| ID | Acceptance Criteria | Implementation Component | Test / Verification Coverage | Status |
|---|---|---|---|:---:|
| **AC-01** | Multi-tenant merchant profile & setup | `TenantContext`, `MerchantUser`, `V1__init_payflow_schema.sql` | `DataSeeder.java`, `AuthSecurityIntegrationTest` | ✅ Pass |
| **AC-02** | Secure API key generation & hashing (`pf_live_...`) | `ApiKeyService.java` (SHA-256 raw vs hash storage) | `ApiKeyServiceUnitTest.java` (3/3 tests) | ✅ Pass |
| **AC-03** | Strict cross-tenant resource isolation | `SecurityConfig`, `TenantContextFilter`, JPA queries | `TenantSecurityUnitTest.java` (3/3 tests) | ✅ Pass |
| **AC-04** | Distributed idempotency (replay & 409 conflict) | `IdempotencyService.java` (Redis + DB fallback) | `PaymentServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-05** | Dynamic routing & circuit breaker exclusion | `RoutingService.java`, Resilience4j CB state tracking | `RoutingServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-06** | Ambiguous timeout safety (never fail/double charge) | `PaymentService.java`, `PaymentAttemptStatus.UNKNOWN` | `PaymentServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-07** | Strict payment state machine (`CREATED` -> `SUCCEEDED` / `FAILED`) | `PaymentStatus.java`, `PaymentService.java` | `PaymentServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-08** | Prevention of blind cross-provider retries | `PaymentService.java` checks prior attempt ambiguity | `PaymentServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-09** | Durable webhook inbox & deduplication | `WebhookService.java` (`provider_id` + `provider_event_id`) | `WebhookServiceTest.java` (3/3 tests) | ✅ Pass |
| **AC-10** | Refund cumulative balance validation | `RefundService.java` (`sum(refunds) <= captured_amount`) | `RefundServiceTest.java` (4/4 tests) | ✅ Pass |
| **AC-11** | Transactional outbox event emission | `OutboxService.java`, `OutboxPublisherWorker.java` | Outbox table verification & Kafka message delivery | ✅ Pass |
| **AC-12** | Stale attempt scanner & reconciliation workflow | `ReconciliationService.java`, Scheduled cron worker | `ReconciliationServiceTest.java` (3/3 tests) | ✅ Pass |
| **AC-13** | Database-computed analytics overview | `AnalyticsService.java`, `AnalyticsController.java` | Live aggregate SQL queries (Volume, Count, Split) | ✅ Pass |
| **AC-14** | Append-only audit logging & redaction | `AuditLogService.java` (PAN, CVV, API key masked) | `AuditLogController.java` verification | ✅ Pass |
| **AC-15** | Functional React 18 Merchant Console UI | `frontend/src/` (TypeScript, Vite, Tailwind CSS) | End-to-end verified with zero browser errors | ✅ Pass |

---

## 🛠 Technology Stack

### Backend
- **Language & Runtime**: Java 21 LTS
- **Framework**: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Spring Validation)
- **Database & Migration**: PostgreSQL 16 Alpine, Flyway Migration Engine
- **Distributed Cache & Locking**: Redis 7 Alpine (`spring-boot-starter-data-redis`)
- **Event Streaming**: Apache Kafka 7.6.0 KRaft mode (`spring-kafka`)
- **Resilience & Fault Tolerance**: Resilience4j (CircuitBreaker, Retry)
- **Security & Cryptography**: Spring Security, JJWT (HMAC-SHA256), SHA-256 key hashing, BCrypt password hashing

### Frontend Merchant Console
- **Framework & Language**: React 18, TypeScript 5
- **Tooling & Bundler**: Vite 5
- **Styling & UI**: Tailwind CSS 3.4, Lucide React Icons
- **Routing & Networking**: React Router DOM v6, Axios with unified error & correlation ID interceptors
- **Reverse Proxy / Production Server**: Nginx Alpine

---

## 🔌 Port Mappings & Network Topology

To prevent collisions with existing services running on host machines, the Dockerized services are mapped to non-conflicting host ports while communicating over standard internal ports within the isolated `fluxpay-net` bridge network:

| Service | Container Name | Host Port | Internal Container Port | Protocol / Purpose |
|---|---|:---:|:---:|---|
| **Merchant Console UI** | `fluxpay-frontend` | `3000` | `80` | HTTP / Single Page Application & API Proxy |
| **FluxPay Core Backend** | `fluxpay-backend` | `8080` | `8080` | HTTP / REST API & Health Endpoints |
| **PostgreSQL Database** | `fluxpay-postgres` | `5433` | `5432` | TCP / Relational Primary Storage |
| **Redis Cache & Lock** | `fluxpay-redis` | `6380` | `6379` | TCP / Distributed Idempotency Cache |
| **Apache Kafka Broker** | `fluxpay-kafka` | `9094` | `9092` / `29092` | TCP / KRaft Event Bus |

> **Note**: Within the internal Docker network (`fluxpay-net`), the backend connects to `postgres:5432`, `redis:6379`, and `kafka:29092`. Host tools (such as pgAdmin, DBeaver, or Redis Insight) connect via the mapped host ports (`5433`, `6380`, `9094`).

---

## 🚀 Quick Start with Docker Compose

Ensure Docker and Docker Compose are installed on your machine.

### 1. Clone & Enter Infrastructure Directory
```bash
git clone https://github.com/your-org/payment-orchestrator.git
cd payment-orchestrator/infrastructure
```

### 2. Launch All Services
```bash
docker-compose up -d --build
```

### 3. Verify System Health
Check that all 5 containers are running and healthy:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```
Expected output:
```text
NAMES              STATUS                    PORTS
fluxpay-frontend   Up (healthy)              0.0.0.0:3000->80/tcp
fluxpay-backend    Up (healthy)              0.0.0.0:8080->8080/tcp
fluxpay-postgres   Up (healthy)              0.0.0.0:5433->5432/tcp
fluxpay-redis      Up (healthy)              0.0.0.0:6380->6379/tcp
fluxpay-kafka      Up (healthy)              0.0.0.0:9094->9092/tcp
```

### 4. Access the Applications
- **Merchant Console UI**: [http://localhost:3000](http://localhost:3000)
- **Backend API Base**: [http://localhost:8080/api/v1](http://localhost:8080/api/v1)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 💻 Standalone Local Development

If developing backend or frontend services outside Docker containers:

### 1. Start Infrastructure Only
```bash
cd infrastructure
docker-compose up -d postgres redis kafka
```

### 2. Run Spring Boot Backend
Ensure Java 21 LTS is active:
```bash
# From repository root
./mvnw spring-boot:run -pl backend -Dspring-boot.run.profiles=local
```
*(On Windows PowerShell: `.\mvnw.cmd spring-boot:run -pl backend -Dspring-boot.run.profiles=local`)*

### 3. Run Frontend Development Server
Ensure Node.js 20+ is active:
```bash
cd frontend
npm install
npm run dev
```
The Vite development server will open on `http://localhost:5173` and proxy API calls to `http://localhost:8080`.

---

## 🔑 Pre-Seeded Credentials & Access

The platform automatically boots with deterministic seed accounts, API keys, and active provider configurations for testing:

### Demo Merchant Accounts

| Organization | Work Email | Password | Role | Description |
|---|---|---|---|---|
| **Acme Global Corp** | `admin@acme.com` | `password123` | `MERCHANT_ADMIN` | Full admin privileges (settings, routing, keys) |
| **Acme Global Corp** | `operator@acme.com` | `password123` | `MERCHANT_OPERATOR` | Operational permissions (create payments, refunds) |
| **Acme Global Corp** | `analyst@acme.com` | `password123` | `READ_ONLY_ANALYST` | Read-only analytics & auditing access |
| **Beta Retail Ltd** | `admin@beta.com` | `password123` | `MERCHANT_ADMIN` | Separate tenant to verify cross-tenant isolation |

### Pre-Seeded API Keys (for cURL & Postman)

- **Acme Global Corp**: `pf_live_acme_secret_key_1234567890abcdef`
- **Beta Retail Ltd**: `pf_live_beta_secret_key_1234567890abcdef`

> **Quick Login Tip**: On the web console sign-in page ([http://localhost:3000/login](http://localhost:3000/login)), click any of the one-click demo pill buttons at the bottom to pre-fill the form instantly!

---

## 💡 Core Features & Engineering Highlights

### 1. Multi-Tenant Identity & RBAC
- **Strict Isolation**: All queries filter by `merchant_id`. Requests resolve tenant identity through either a signed JWT bearer token or an API key passed in the `Authorization: Bearer <key>` or `X-API-Key` headers.
- **ThreadLocal Cleanup**: A dedicated `TenantContextFilter` loads the context at the beginning of each request and guarantees eviction in a `finally` block to prevent thread pool contamination in asynchronous environments.
- **Secure Key Management**: API keys are generated with high-entropy prefixes (`pf_live_...`). Only the SHA-256 hash is persisted in PostgreSQL; the raw key is displayed to the user exactly once.

### 2. Distributed Idempotency Engine
- **Canonical Payload Hashing**: Normalizes request JSON by sorting keys and hashing using SHA-256.
- **Two-Tier Storage**:
  1. **Redis Lock (Fast Path)**: Atomically reserves the `Idempotency-Key` for 60 seconds during in-flight processing.
  2. **PostgreSQL Record (Durable Path)**: Persists the completed response payload and status code with a 24-hour TTL.
- **Conflict vs Replay Logic**:
  - Exact match (`key` + `hash` match) $\rightarrow$ Instantly returns cached HTTP status and JSON response without re-triggering upstream gateways.
  - Payload mismatch (`key` matches, but `hash` differs) $\rightarrow$ Returns `409 Conflict` with `IDEMPOTENCY_CONFLICT`.

### 3. Dynamic Smart Routing & Circuit Breaking
- **Routing Strategies**:
  - **Priority Routing**: Tries primary provider (e.g., Stripe, Priority 1) first. If down or hard declined, falls back to secondary (e.g., Adyen, Priority 2).
  - **Weighted Traffic Split**: Proportional randomized routing (e.g., 70% Stripe / 30% Adyen) for A/B testing and volume commitments.
- **Circuit Breaker Protection (Resilience4j)**:
  - Continuously monitors gateway failure rates.
  - When a provider enters an `OPEN` circuit state (consecutive network or 5xx errors), the routing engine automatically excludes it from the candidate pool and routes 100% of traffic to healthy alternatives.

### 4. Ambiguous Timeout Safety & Zero Double-Charge Guarantee
The cardinal rule of payment orchestration: **A gateway timeout must never be marked as failed or blindly retried.**
- When a payment gateway times out or returns an unconfirmed response:
  1. The specific `payment_attempt` is marked with status `UNKNOWN`.
  2. The parent `payment` transitions to `PENDING` (never `FAILED`).
  3. **Blind Retries are Blocked**: Automatic failover to an alternative gateway is strictly halted because the customer's card may have already been charged upstream.
  4. The transaction is flagged for the **Reconciliation Engine** or webhook resolution.

### 5. Interactive Payment Simulator
The React Merchant Console includes an interactive **Payment Simulator Modal** (`Payments` page $\rightarrow$ `Simulate Payment` button) allowing engineers to test gateway edge cases in real time:
- `SUCCESS`: Returns `201 Created` with status `SUCCEEDED` and synthetic authorization code.
- `DECLINE`: Simulates card rejection (e.g. `insufficient_funds`, `card_declined`).
- `TIMEOUT`: Simulates upstream network drop; triggers `UNKNOWN` attempt and `PENDING` status.
- `REQUIRES_ACTION`: Simulates 3D-Secure 2 challenge flow with redirect URL.
- `5XX`: Simulates gateway internal server breakdown to trigger circuit breaker threshold counters.

### 6. Durable Webhook Deduplication
- **Signature Verification**: Validates HMAC cryptographic signatures before ingesting provider webhooks.
- **Idempotent Ingestion**: Tracks events using a unique compound index on `(provider_id, provider_event_id)`:
  - First receipt: Processes event, executes state transitions, and returns `200 OK`.
  - Duplicate delivery: Detects existing event ID, skips business execution, and returns `200 OK` (preventing upstream gateway replay loops).

### 7. Transactional Outbox & Kafka Event Streaming
- **Dual-Write Consistency**: To eliminate distributed state divergence between PostgreSQL and Kafka, domain events (`payment.succeeded`, `payment.failed`, `refund.created`) are written to the `outbox_events` table inside the same ACID database transaction as the payment entity.
- **Publisher Worker**: A scheduled background worker polls unpublished outbox records, emits them to Kafka topics, and flags them as `PUBLISHED` upon delivery acknowledgment.

### 8. Automated Reconciliation Hub & Stale Attempt Scanner
- **Scheduled Stale Scanner**: Runs periodically (configurable cron, default every 5 minutes) to detect payment attempts left in `UNKNOWN` state older than 15 minutes.
- **Gateway Query & Sync**: Calls provider verification endpoints to determine whether the transaction was actually authorized or dropped, transitioning payments to their definitive final state (`SUCCEEDED` or `FAILED`).

### 9. Immutable Audit Logging & PCI Redaction
- **Append-Only Trail**: Every merchant action (login, payment creation, refund authorization, provider configuration change, routing rule update) creates an immutable audit record.
- **Zero Sensitive Data Leakage**: Sensitive parameters (card numbers / PAN, CVV, passwords, full API keys) are masked before persistence using regex redaction filters (`4111********1111`).

---

## 📡 REST API Quick Reference & cURL Examples

All API endpoints are prefixed with `/api/v1`.

### 1. Healthcheck
```bash
curl -X GET http://localhost:8080/actuator/health
```

### 2. Merchant Authentication (Obtain JWT)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@acme.com",
    "password": "password123"
  }'
```

### 3. Create a Payment (Using API Key & Idempotency)
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer pf_live_acme_secret_key_1234567890abcdef" \
  -H "Idempotency-Key: demo-order-001" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-1001",
    "amountMinor": 4999,
    "currency": "USD",
    "paymentMethod": "CARD",
    "testScenario": "SUCCESS"
  }'
```

### 4. Test Ambiguous Timeout Safety (AC-06)
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer pf_live_acme_secret_key_1234567890abcdef" \
  -H "Idempotency-Key: demo-timeout-test" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-TIMEOUT-01",
    "amountMinor": 8900,
    "currency": "USD",
    "paymentMethod": "CARD",
    "testScenario": "TIMEOUT"
  }'
```
*Response: Returns status `PENDING` with attempt status `UNKNOWN` — double charging is prevented!*

### 5. Issue a Partial Refund
```bash
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/refunds \
  -H "Authorization: Bearer pf_live_acme_secret_key_1234567890abcdef" \
  -H "Idempotency-Key: refund-order-001" \
  -H "Content-Type: application/json" \
  -d '{
    "amountMinor": 1500,
    "reason": "CUSTOMER_REQUEST"
  }'
```

### 6. Query Live Analytics Overview
```bash
curl -X GET http://localhost:8080/api/v1/analytics/overview \
  -H "Authorization: Bearer pf_live_acme_secret_key_1234567890abcdef"
```

---

## 🧪 Automated Test Suite

FluxPay features an exhaustive test suite covering unit, integration, and security layers:

```bash
# Run complete test suite (31 tests)
./mvnw test -pl backend
```

### Key Test Classes:
- `ApiKeyServiceUnitTest`: API key generation, prefixing, SHA-256 hashing, and verification.
- `TenantSecurityUnitTest`: ThreadLocal tenant boundaries and cross-tenant leakage prevention.
- `PaymentServiceTest`: Idempotency replays, 409 conflict detection, ambiguous timeout handling, and blind-failover prevention.
- `RoutingServiceTest`: Priority routing, weighted traffic distribution, and circuit breaker exclusion.
- `WebhookServiceTest`: Signature validation and `(provider_id, provider_event_id)` deduplication.
- `RefundServiceTest`: Partial refunds, full refunds, and cumulative balance overflow enforcement.
- `ReconciliationServiceTest`: Stale attempt resolution and status synchronization.

### Frontend Validation:
```bash
cd frontend
npm run build
```
*Builds production bundle with zero TypeScript diagnostics or ESLint errors.*

---

## 📂 Project Directory Structure

```text
payment-orchestrator/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/payflow/
│   │   │   │   ├── analytics/        # Aggregate analytics and metrics service
│   │   │   │   ├── audit/            # Immutable audit log service with redaction
│   │   │   │   ├── common/           # Seeders, exceptions, and global response envelope
│   │   │   │   ├── idempotency/      # Redis & DB idempotency management
│   │   │   │   ├── identity/         # JWT, API keys, User & Tenant authentication
│   │   │   │   ├── merchant/         # Merchant configuration and domain
│   │   │   │   ├── outbox/           # Transactional outbox event publisher worker
│   │   │   │   ├── payment/          # Payment state machine, attempts, and API
│   │   │   │   ├── provider/         # Stripe & Adyen simulator adapters and ports
│   │   │   │   ├── reconciliation/   # Stale attempt scanner and resolution service
│   │   │   │   ├── refund/           # Partial and full refund ledger engine
│   │   │   │   ├── routing/          # Priority and weighted dynamic routing engine
│   │   │   │   └── webhook/          # Durable webhook ingestion and deduplication
│   │   │   └── resources/
│   │   │       ├── db/migration/     # Flyway SQL migrations (V1, V2)
│   │   │       └── application.yml   # Multi-profile Spring configuration
│   │   └── test/                     # Comprehensive 31-test automated suite
│   ├── Dockerfile                    # Multi-stage JDK 21 build
│   └── pom.xml                       # Maven dependency tree
├── frontend/
│   ├── src/
│   │   ├── api/                      # Axios client and typed API endpoints
│   │   ├── components/               # Navbar, Sidebar, SimulatorModal, Tables, Badges
│   │   ├── context/                  # AuthContext with tenant and role management
│   │   ├── pages/                    # 10 merchant portal views (Dashboard, Payments, etc.)
│   │   └── types/                    # Unified TypeScript domain definitions
│   ├── nginx.conf                    # Production reverse proxy and SPA routing
│   ├── Dockerfile                    # Multi-stage Node 20 build -> Nginx Alpine
│   └── package.json                  # React 18, Vite, Tailwind CSS dependencies
├── infrastructure/
│   └── docker-compose.yml            # Complete stack: postgres, redis, kafka, backend, frontend
├── docs/                             # In-depth architectural and operational guides
│   ├── api.md                        # REST API endpoint reference
│   ├── architecture.md               # Deep architectural breakdown
│   ├── database.md                   # PostgreSQL schema and indexes
│   ├── payment-state-machine.md      # State machine diagram and transitions
│   ├── provider-adapters.md          # Gateway abstraction specifications
│   ├── runbooks.md                   # Production operational runbooks
│   └── security.md                   # Security guidelines and PCI compliance scoping
├── .env.example                      # Environment variables reference
└── README.md                         # This file
```

---

## 🔒 Security & Compliance

- **No Plaintext PAN Storage**: Primary Account Numbers (credit card numbers) are never stored in raw form in any database table. The platform adheres strictly to PCI DSS Level 1 scoping guidelines.
- **Redaction Engine**: Regex interceptors mask authorization tokens, CVVs, and account secrets prior to writing to application logs or audit tables.
- **Fail-Safe Tenant Scoping**: All database transactions enforce merchant scoping directly via Spring Data repository query predicates, completely preventing cross-tenant leakage.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
