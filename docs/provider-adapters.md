# Payment Provider Abstraction & Gateway Integrations

## 1. Provider Port Contract (`PaymentProviderPort`)

All payment service provider (PSP) integrations implement the enterprise `PaymentProviderPort`:

```java
public interface PaymentProviderPort {
    String getProviderCode();
    ProviderCapabilities capabilities();
    InitiatePaymentResult initiatePayment(InitiatePaymentCommand command);
    ProviderPaymentStatus getPaymentStatus(String providerReference);
    default ProviderCancelResult cancelPayment(String providerReference);
    ProviderRefundResult refund(ProviderRefundCommand command);
    boolean verifyWebhook(Map<String, String> headers, String rawBody);
    ParsedWebhookEvent parseWebhook(String rawBody);
}
```

---

## 2. Integrated Gateway Adapters

### 2.1 Stripe Adapter (`stripe`)
- **Integration**: Official Stripe Java SDK (`com.stripe:stripe-java`) with Payment Intents API.
- **Supported Currencies**: `USD`, `EUR`, `GBP`, `INR`, `SGD`
- **Supported Methods**: `CARD`
- **Features**:
  - PaymentIntent creation with automatic payment methods & metadata mapping
  - Payment status retrieval via `PaymentIntent.retrieve`
  - Payment cancellation via `PaymentIntent.cancel`
  - Full & partial refunds via `Refund.create`
  - Webhook verification via official `Webhook.constructEvent` with `Stripe-Signature`
  - Dual-mode execution: live sandbox API calls or deterministic simulation scenarios (`SUCCESS`, `DECLINE`, `TIMEOUT`, `REQUIRES_ACTION`, `5XX`).

### 2.2 Razorpay Gateway Adapter (`razorpay`)
- **Integration**: Official Razorpay Java SDK (`com.razorpay:razorpay-java`).
- **Supported Currencies**: `INR`, `USD`, `EUR`
- **Supported Methods**: `CARD`, `UPI`, `NETBANKING`, `WALLET`
- **Features**:
  - Order creation via `client.orders.create` with amount in paise
  - Hosted / checkout public URL and client metadata (`keyId`, `orderId`)
  - Status retrieval via `client.orders.fetch` or `client.payments.fetch`
  - Full & partial refunds via `client.payments.refund`
  - Cryptographic webhook signature verification via HMAC-SHA256 (`x-razorpay-signature`)
  - Webhook event parsing for `order.paid`, `payment.captured`, and `payment.failed`.

### 2.3 PayU India Gateway Adapter (`payu`)
- **Integration**: PayU India Hosted Checkout & REST postservice API.
- **Supported Currencies**: `INR`
- **Supported Methods**: `CARD`, `UPI`, `NETBANKING`, `WALLET`
- **Features**:
  - Request hash generation using SHA-512: `sha512(key|txnid|amount|productinfo|firstname|email|udf1|...|udf5||||||salt)`
  - Hosted checkout form parameter generation
  - Status verification API via `verify_payment` command
  - Refund API via `cancel_refund_transaction` command
  - Reverse response hash verification: `sha512(salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)`
  - Webhook ingestion and attempt synchronization.

### 2.4 Cashfree Payments Adapter (`cashfree`)
- **Integration**: Cashfree PG Orders API (v2023-08-01).
- **Supported Currencies**: `INR`, `USD`
- **Supported Methods**: `CARD`, `UPI`, `NETBANKING`, `WALLET`
- **Features**:
  - Order creation (`POST /orders`) returning `cf_order_id`, `payment_session_id`, and checkout payment link
  - Order status retrieval (`GET /orders/{order_id}`)
  - Refund API (`POST /orders/{order_id}/refunds`)
  - Webhook signature verification: HMAC-SHA256 computed over `timestamp + rawBody` with secret key (`x-webhook-signature`, `x-webhook-timestamp`)
  - Event normalization for `PAYMENT_SUCCESS_WEBHOOK` and `PAYMENT_FAILED_WEBHOOK`.

---

## 3. Circuit Breaker Integration (Resilience4j)

Every provider adapter is guarded by a Resilience4j Circuit Breaker named after the provider code (`stripe`, `razorpay`, `payu`, `cashfree`, `adyen`).
- **Sliding Window Size**: 10 calls
- **Minimum Number of Calls**: 5
- **Failure Rate Threshold**: 50%
- **Wait Duration in Open State**: 10 seconds
- While `OPEN`, the `RoutingService` automatically excludes the failing provider and routes traffic to alternative healthy candidates.
