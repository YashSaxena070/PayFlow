package com.payflow.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyDto(
        UUID id,
        String keyPrefix,
        String rawSecretKey, // Only present immediately upon creation
        String scopes,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        boolean active
) {}

