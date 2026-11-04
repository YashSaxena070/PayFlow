-- PayFlow Default Providers Seeding
-- Seed Stripe and Adyen simulator/sandbox provider records

INSERT INTO providers (id, code, display_name, enabled, adapter_type, capabilities_json, created_at)
VALUES 
(
    'a0000000-0000-0000-0000-000000000001',
    'stripe',
    'Stripe Sandbox / Simulator',
    TRUE,
    'STRIPE',
    '{"supportedCurrencies":["USD","EUR","GBP","INR","SGD"],"supportedMethods":["CARD"],"supports3DS":true,"supportsRefunds":true,"supportsWebhooks":true}',
    CURRENT_TIMESTAMP
),
(
    'a0000000-0000-0000-0000-000000000002',
    'adyen',
    'Adyen Sandbox / Simulator',
    TRUE,
    'ADYEN',
    '{"supportedCurrencies":["USD","EUR","GBP","INR","SGD"],"supportedMethods":["CARD","UPI","NETBANKING"],"supports3DS":true,"supportsRefunds":true,"supportsWebhooks":true}',
    CURRENT_TIMESTAMP
);

