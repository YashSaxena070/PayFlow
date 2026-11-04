package com.payflow.identity.dto;

import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        String email,
        UUID merchantId,
        String merchantName,
        String role
) {}

