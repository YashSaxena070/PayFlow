# ADR 001: Modular Monolith Architecture

## Status
Accepted

## Context
PayFlow requires orchestrating payment operations across multiple external payment gateways, maintaining high reliability, multi-tenant isolation, idempotency, webhook processing, reconciliation, and event streaming. A microservices architecture at this stage would introduce distributed transaction overhead, deployment complexity, and network latency without clear operational necessity for a single-team or portfolio release.

## Decision
We adopt a **modular monolith** using Java 21 LTS and Spring Boot 3.4.x.
Internal modules are structured around domain boundaries:
- `identity`
- `merchant`
- `payment`
- `routing`
- `provider`
- `idempotency`
- `webhook`
- `refund`
- `reconciliation`
- `outbox`
- `audit`
- `reporting`

Communication between modules happens via well-defined domain ports and application services. Asynchronous communication to external subscribers is handled via a Transactional Outbox pattern backed by Kafka.

## Consequences
- Single deployable jar with low operational footprint.
- Strong compile-time module boundaries.
- Easy to extract independent services in the future if scale demands it.
- Seamless local developer onboarding using Docker Compose.

