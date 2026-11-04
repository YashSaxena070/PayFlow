export type PaymentStatus =
  | 'CREATED'
  | 'PROCESSING'
  | 'REQUIRES_ACTION'
  | 'PENDING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

export type AttemptStatus =
  | 'CREATED'
  | 'SUBMITTED'
  | 'REQUIRES_ACTION'
  | 'PENDING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'UNKNOWN'
  | 'RECONCILIATION_REQUIRED';

export interface PaymentAttempt {
  id: string;
  providerCode: string;
  attemptNumber: number;
  status: AttemptStatus;
  providerReference?: string;
  amountMinor: number;
  failureCode?: string;
  failureMessageSafe?: string;
  submittedAt: string;
  completedAt?: string;
}

export interface Payment {
  id: string;
  merchantId: string;
  orderId: string;
  amountMinor: number;
  currency: string;
  paymentMethod: string;
  status: PaymentStatus;
  idempotencyKey: string;
  attempts: PaymentAttempt[];
  redirectUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProviderDto {
  id: string;
  code: string;
  displayName: string;
  active: boolean;
  merchantEnabled: boolean;
  priority: number;
  weight: number;
  supportedCurrencies: string[];
  supportedMethods: string[];
  supports3DS: boolean;
  supportsRefunds: boolean;
  supportsWebhooks: boolean;
}

export interface RoutingRuleDto {
  id: string;
  name: string;
  strategy: 'PRIORITY' | 'WEIGHTED';
  conditionsJson: string;
  priority: number;
  enabled: boolean;
  version: number;
  createdAt: string;
}

export interface RefundDto {
  id: string;
  paymentId: string;
  merchantId: string;
  amountMinor: number;
  currency: string;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED';
  idempotencyKey: string;
  providerRefundReference?: string;
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReconciliationCaseDto {
  id: string;
  expectedStatus: string;
  providerStatus?: string;
  caseStatus: 'OPEN' | 'RESOLVED' | 'AUTO_RESOLVED';
  resolution?: string;
  resolutionReason?: string;
  createdAt: string;
  resolvedAt?: string;
  payment?: {
    id: string;
    orderId: string;
    amountMinor: number;
    currency: string;
  };
  attempt?: {
    id: string;
    providerReference?: string;
    status: string;
  };
}

export interface AuditLogDto {
  id: string;
  actorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  metadataRedacted: string;
  correlationId: string;
  createdAt: string;
}

export interface ApiKeyDto {
  id: string;
  name: string;
  keyPrefix: string;
  role: string;
  revoked: boolean;
  createdAt: string;
  expiresAt?: string;
  secretKey?: string;
}

export interface AnalyticsOverview {
  totalVolumeMinor: number;
  totalTransactions: number;
  successfulTransactions: number;
  failedTransactions: number;
  pendingTransactions: number;
  successRatePercent: number;
  providerMetrics: Array<{
    providerCode: string;
    totalAttempts: number;
    successfulAttempts: number;
    failedAttempts: number;
    successRatePercent: number;
  }>;
}

export interface UserSession {
  userId: string;
  email: string;
  role: string;
  merchantId: string;
  token: string;
}

