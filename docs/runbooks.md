# PayFlow Enterprise Operational Runbooks

## Runbook 1: Ambiguous Timeout & Reconciliation Resolution

### Context
When an external payment gateway experiences network instability or drops an in-flight socket connection, PayFlow marks the attempt `UNKNOWN` and the payment `PENDING`.

### Steps:
1. Navigate to the **Reconciliation** tab in the Merchant Console (`/reconciliation`).
2. Click **Scan Stale Transactions**.
   - The platform polls the upstream provider (`getPaymentStatus`) for any attempts older than 3 minutes.
   - If the provider confirms `SUCCEEDED` or `FAILED`, the case is marked `AUTO_RESOLVED` and the payment status updates automatically.
3. If the case remains `OPEN`:
   - Inspect the payment ID in the transaction list.
   - Check the upstream processor portal (or test simulator log) using the `providerReference`.
   - Click **Resolve** and choose:
     - `MARK_SUCCEEDED` if funds were captured at the bank.
     - `MARK_FAILED` if the transaction was rejected or dropped.
   - Enter mandatory audit justification.

---

## Runbook 2: Webhook Replay & Failure Triage

### Context
When upstream webhooks fail delivery due to temporary network partitions or invalid certificates:

### Steps:
1. Ingested webhooks are logged in `webhook_events`.
2. Inspect the webhook inbox via database or management API:
   ```bash
   GET /api/v1/webhooks
   ```
3. To replay an unconfirmed webhook:
   ```bash
   POST /api/v1/webhooks/{id}/replay
   ```
4. Note: Duplicate webhooks with the same `(provider_id, provider_event_id)` are automatically deduplicated with HTTP 200 OK and zero duplicate side-effects.

---

## Runbook 3: Gateway Outage & Dynamic Circuit Breaker Failover

### Context
When an upstream provider (e.g. Stripe or Adyen) experiences widespread degradation:

### Steps:
1. Resilience4j circuit breakers monitor error rates in real-time.
2. When the failure rate exceeds 50%, the circuit transitions to `OPEN`.
3. The routing engine automatically excludes the failing provider from eligible candidates and shifts traffic to alternate active gateways (e.g. from Stripe to Adyen).
4. In the Merchant Console (`/providers`), operators can also manually toggle the provider `enabled` switch to `OFF` to force zero traffic allocation.

