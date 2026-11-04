# PayFlow Enterprise REST API Specification

Base URL: `/api/v1`

## Standard Response Envelope

All API endpoints return JSON conforming to the PayFlow standard envelope:

```json
{
  "data": { ... },
  "meta": {
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-11-04T02:30:00Z"
  }
}
```

Error responses:

```json
{
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Key was already used with a different request"
  },
  "meta": {
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-11-04T02:30:00Z"
  }
}
```

---

## 1. Authentication & Security

### 1.1 Login
`POST /auth/login`
- Body: `{"email": "admin@acme.com", "password": "password123"}`
- Returns JWT token and session user details.

### 1.2 Get Current User
`GET /auth/me`
- Requires: Bearer token

### 1.3 Manage API Keys
- `GET /api-keys`: List active API keys for merchant.
- `POST /api-keys`: Create new API key (`name`, `role`). Plaintext secret returned once!
- `DELETE /api-keys/{id}`: Revoke an existing API key.

---

## 2. Payments Lifecycle

### 2.1 Initiate Payment
`POST /payments`
- Headers:
  - `Idempotency-Key` (required): Unique UUID or merchant transaction reference.
  - `Authorization`: `Bearer <token>` or `pf_live_<key>`
- Body:
```json
{
  "orderId": "ORD-998822",
  "amountMinor": 12500,
  "currency": "USD",
  "paymentMethod": "CARD",
  "returnUrl": "https://merchant.example.com/checkout/return",
  "testScenario": "SUCCESS"
}
```
- Test Scenarios for Simulators:
  - `SUCCESS`: Returns HTTP 201 with `SUCCEEDED` status.
  - `DECLINE`: Returns `FAILED` with failure code.
  - `TIMEOUT`: Returns `PENDING` with attempt in `UNKNOWN` state (AC-06).
  - `REQUIRES_ACTION`: Returns `REQUIRES_ACTION` with 3DS redirectUrl.
  - `5XX`: Upstream server error.

### 2.2 Get Payment Details
`GET /payments/{id}`
- Returns payment details and attempt summary. Scoped to authenticated merchant.

### 2.3 Search Payments
`GET /payments?page=0&size=20&status=SUCCEEDED&currency=USD&orderId=ORD`
- Server-side pagination and filtering.

### 2.4 Get Payment Attempts
`GET /payments/{id}/attempts`
- Returns chronological list of provider attempts for the transaction.

---

## 3. Providers & Routing Policies

### 3.1 List Providers
`GET /providers`
- Returns all platform providers with merchant enablement status, priority, and weight. Secrets are fully redacted.

### 3.2 Update Provider Configuration
`PATCH /providers/{providerId}/configuration`
- Body:
```json
{
  "enabled": true,
  "priority": 1,
  "weight": 80
}
```

### 3.3 Manage Routing Rules
- `GET /routing-rules`: List active merchant routing rules.
- `POST /routing-rules`: Create new rule (`PRIORITY` or `WEIGHTED`).
- `PUT /routing-rules/{id}`: Update rule configuration.

---

## 4. Webhooks & Inbox

### 4.1 Ingest Webhook
`POST /webhooks/{providerCode}`
- Public endpoint called by payment gateways (Stripe, Adyen).
- Validates cryptographic signature header (`Stripe-Signature` or `X-Adyen-Signature`).
- Deduplicates on `(provider_id, provider_event_id)`: returns 200 OK immediately for duplicate deliveries.

### 4.2 Replay Webhook
`POST /webhooks/{id}/replay`
- Operator endpoint to re-evaluate processed webhooks.

---

## 5. Refunds

### 5.1 Issue Refund
`POST /payments/{paymentId}/refunds`
- Headers: `Idempotency-Key` (required)
- Body:
```json
{
  "amountMinor": 5000,
  "reason": "Customer cancellation"
}
```
- Invariant: `sum(refunds) <= captured_amount`. Exceeding balance returns 422 `REFUND_EXCEEDS_PAYMENT_AMOUNT`.

### 5.2 List Refunds
`GET /refunds?page=0&size=20`

---

## 6. Reconciliation & Operations

### 6.1 List Reconciliation Cases
`GET /reconciliation-cases`
- Returns `OPEN`, `RESOLVED`, and `AUTO_RESOLVED` discrepancy cases.

### 6.2 Trigger Stale Transaction Scan
`POST /reconciliation-cases/scan?staleSeconds=300`
- Queries attempts stuck in `UNKNOWN`/`PENDING` beyond the threshold.

### 6.3 Resolve Case
`POST /reconciliation-cases/{id}/resolve`
- Body: `{"resolution": "MARK_FAILED", "reason": "Confirmed charge was not captured at bank"}`

---

## 7. Analytics & Audit Logs

### 7.1 Analytics Overview
`GET /analytics/overview?fromDate=...&toDate=...`
- Returns aggregated KPIs: total volume, transaction counts, overall success rate, and per-provider success rates.

### 7.2 Audit Logs
`GET /audit-logs?page=0&size=50`
- Returns append-only log with redacted sensitive fields.

