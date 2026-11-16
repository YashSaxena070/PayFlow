-- PayFlow V3 Migration: Seed Indian Payment Gateways (Razorpay, PayU, Cashfree)
-- Ensures all 4 primary gateways (Stripe, Razorpay, PayU, Cashfree) are registered

INSERT INTO providers (id, code, display_name, enabled, adapter_type, capabilities_json, created_at)
VALUES 
(
    'a0000000-0000-0000-0000-000000000003',
    'razorpay',
    'Razorpay Gateway',
    TRUE,
    'RAZORPAY',
    '{"supportedCurrencies":["INR","USD","EUR"],"supportedMethods":["CARD","UPI","NETBANKING","WALLET"],"supports3DS":true,"supportsRefunds":true,"supportsWebhooks":true}',
    CURRENT_TIMESTAMP
),
(
    'a0000000-0000-0000-0000-000000000004',
    'payu',
    'PayU India Gateway',
    TRUE,
    'PAYU',
    '{"supportedCurrencies":["INR"],"supportedMethods":["CARD","UPI","NETBANKING","WALLET"],"supports3DS":true,"supportsRefunds":true,"supportsWebhooks":true}',
    CURRENT_TIMESTAMP
),
(
    'a0000000-0000-0000-0000-000000000005',
    'cashfree',
    'Cashfree Payments',
    TRUE,
    'CASHFREE',
    '{"supportedCurrencies":["INR","USD"],"supportedMethods":["CARD","UPI","NETBANKING","WALLET"],"supports3DS":true,"supportsRefunds":true,"supportsWebhooks":true}',
    CURRENT_TIMESTAMP
);
