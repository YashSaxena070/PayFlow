package com.payflow.identity;

import com.payflow.identity.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private final JwtTokenService jwtTokenService = new JwtTokenService(
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            3600000
    );

    @Test
    @DisplayName("Should generate valid JWT and parse claims correctly")
    void testTokenGenerationAndParsing() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String email = "admin@acme.com";
        String role = "MERCHANT_ADMIN";

        String token = jwtTokenService.generateToken(userId, merchantId, email, role);
        assertNotNull(token);
        assertTrue(jwtTokenService.validateToken(token));

        Claims claims = jwtTokenService.parseToken(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(merchantId.toString(), claims.get("merchantId"));
        assertEquals(email, claims.get("email"));
        assertEquals(role, claims.get("role"));
    }

    @Test
    @DisplayName("Should reject malformed JWT token")
    void testMalformedToken() {
        assertFalse(jwtTokenService.validateToken("invalid.token.here"));
    }
}

