package com.payflow.identity.security;

import com.payflow.common.tenant.TenantContext;
import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.service.ApiKeyService;
import com.payflow.identity.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PayflowAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;

    public PayflowAuthenticationFilter(JwtTokenService jwtTokenService, ApiKeyService apiKeyService) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            String apiKeyHeader = request.getHeader("X-API-KEY");

            if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
                authenticateWithApiKey(apiKeyHeader);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (token.startsWith("pf_live_") || token.startsWith("pf_test_")) {
                    authenticateWithApiKey(token);
                } else {
                    authenticateWithJwt(token);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticateWithApiKey(String rawKey) {
        Optional<ApiCredential> credentialOpt = apiKeyService.authenticateApiKey(rawKey);
        if (credentialOpt.isPresent()) {
            ApiCredential cred = credentialOpt.get();
            UUID merchantId = cred.getMerchant().getId();

            TenantContext.set(merchantId, null, "api-key:" + cred.getKeyPrefix(), "MERCHANT_ADMIN", true);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "api-key:" + cred.getKeyPrefix(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private void authenticateWithJwt(String token) {
        try {
            if (jwtTokenService.validateToken(token)) {
                Claims claims = jwtTokenService.parseToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                String merchantIdStr = claims.get("merchantId", String.class);
                UUID merchantId = merchantIdStr != null ? UUID.fromString(merchantIdStr) : null;
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                TenantContext.set(merchantId, userId, email, role, false);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ignored) {
            // Unauthenticated request; Spring Security chain enforces access
        }
    }
}

