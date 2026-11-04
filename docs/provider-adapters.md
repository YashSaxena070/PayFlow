# Payment Provider Abstraction & Simulators

## 1. Provider Port Contract (`PaymentProviderPort`)

All payment gateway integrations implement `PaymentProviderPort`:

```java
public interface PaymentProviderPort {
    String getProviderCode();
    ProviderCapabilities capabilities();
    InitiatePaymentResult initiatePayment(InitiatePaymentCommand command);
    ProviderPaymentStatus getPaymentStatus(String providerReference);
    ProviderRefundResult refund(ProviderRefundCommand command);
    boolean verifyWebhook(Map<String, String> headers, String rawBody);
    ParsedWebhookEvent parseWebhook(String rawBody);
}
```

---

## 2. Integrated Adapters & Test Simulators

### 2.1 Stripe Simulator Adapter (`STRIPE`)
- **Supported Currencies**: `USD`, `EUR`, `GBP`, `CAD`, `AUD`, `SGD`, `JPY`
- **Supported Methods**: `CARD`
- **Capabilities**: 3D Secure 2.0, Refunds API, Webhooks
- **Simulated Test Scenarios**:
  - `SUCCESS`: Returns `pi_stripe_...` reference with `SUCCEEDED` status.
  - `DECLINE`: Returns `do_not_honor` failure code with safe merchant message.
  - `TIMEOUT`: Simulates HTTP socket timeout; adapter returns `UNKNOWN` status.
  - `REQUIRES_ACTION`: Returns 3DS redirect authorization URL.
  - `5XX`: Simulates Stripe API 500 internal server error.
- **Webhook Verification**: Validates `Stripe-Signature` header using HMAC-SHA256 signature verification.

### 2.2 Adyen Simulator Adapter (`ADYEN`)
- **Supported Currencies**: `USD`, `EUR`, `GBP`, `INR`, `SGD`, `AED`
- **Supported Methods**: `CARD`, `UPI`, `NETBANKING`
- **Capabilities**: 3D Secure 2.0, Refunds API, Webhooks
- **Simulated Test Scenarios**:
  - `SUCCESS`: Returns `ps_adyen_...` reference with `SUCCEEDED` status.
  - `DECLINE`: Returns `Refused` error with safe message.
  - `TIMEOUT`: Simulates gateway gateway timeout; adapter returns `UNKNOWN` status.
  - `REQUIRES_ACTION`: Returns 3DS redirect URL.
  - `5XX`: Simulates upstream 503 Service Unavailable.
- **Webhook Verification**: Validates `X-Adyen-Signature` header using HMAC-SHA256 signature verification.

---

## 3. Circuit Breaker Integration

Every provider adapter is guarded by a Resilience4j Circuit Breaker named after the provider code (e.g. `stripe`, `adyen`).
- When failure rate exceeds threshold (50%), circuit transitions to `OPEN`.
- While `OPEN`, the `RoutingService` automatically excludes the provider from routing candidates and re-routes traffic to available providers.

