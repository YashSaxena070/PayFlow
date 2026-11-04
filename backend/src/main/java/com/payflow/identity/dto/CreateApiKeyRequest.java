package com.payflow.identity.dto;

import java.time.Instant;

public record CreateApiKeyRequest(
        String scopes,
        Instant expiresAt
) {}

