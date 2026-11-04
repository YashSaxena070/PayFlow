# PayFlow Security & Multi-Tenant Isolation Architecture

## 1. Zero Raw Cardholder Data Policy (PCI DSS Scoped)

PayFlow strictly operates as an orchestration gateway:
- Raw primary account numbers (PAN), CVVs, and magnetic stripe tracks **are never ingested, logged, or persisted**.
- All card numbers in simulator requests are mocked or tokenized.
- Audit logs and request tracing filters automatically strip and redact keys containing sensitive words (`cvv`, `password`, `pan`, `secret`, `key`, `apiKey`, `token`).

---

## 2. Multi-Tenancy Isolation (AC-03)

- **ThreadLocal Scoping**: Every incoming authenticated request sets `TenantContext` containing `merchantId`, `userId`, and `role`.
- **Database Query Partitioning**: Every repository query mutates or reads data strictly scoped by `merchant.id = :merchantId`.
- **Cross-Tenant Attack Rejection**: Verified with automated test `TenantSecurityUnitTest`:
  - Requesting Merchant A's payment ID using Merchant B's token returns HTTP `404 Not Found` (never leaks data across tenants).
  - Attempting to issue a refund or modify provider config across tenant boundaries is rejected.

---

## 3. Cryptographic Authentication & Role Hierarchy

### 3.1 Session Authentication
- High-entropy JSON Web Tokens signed with HMAC-SHA256 (`HS256`).
- Password storage using **BCrypt** with adaptive work factor (`strength = 12`).

### 3.2 Programmatic API Keys
- Keys generated with prefix `pf_live_` followed by 32 cryptographically secure random bytes (Base64URL).
- Only the **SHA-256 hash** of the secret is stored in the database.
- Plaintext secrets are displayed **exactly once** upon generation.

### 3.3 Role-Based Access Control (RBAC)
- `PLATFORM_ADMIN`: Global system oversight.
- `MERCHANT_ADMIN`: Tenant management, user invitations, provider configuration, API key generation.
- `MERCHANT_OPERATOR`: Create payments, issue refunds, resolve reconciliation cases.
- `READ_ONLY_ANALYST`: View transactions, dashboard KPIs, and audit logs.

