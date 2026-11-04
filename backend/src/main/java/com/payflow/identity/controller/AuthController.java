package com.payflow.identity.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.LoginResponse;
import com.payflow.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(), request.merchantId());
        LoginResponse response = new LoginResponse(
                result.token(),
                result.userId(),
                result.email(),
                result.merchantId(),
                result.merchantName(),
                result.role()
        );
        return ResponseEntity.ok(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {
        TenantContext.TenantInfo tenantInfo = TenantContext.get();
        if (tenantInfo == null) {
            return ResponseEntity.ok(ApiResponse.of(Map.of(), CorrelationIdContext.get()));
        }
        Map<String, Object> data = Map.of(
                "merchantId", tenantInfo.merchantId() != null ? tenantInfo.merchantId() : "",
                "userId", tenantInfo.userId() != null ? tenantInfo.userId() : "",
                "email", tenantInfo.email() != null ? tenantInfo.email() : "",
                "role", tenantInfo.role() != null ? tenantInfo.role() : "",
                "isApiKey", tenantInfo.isApiKey()
        );
        return ResponseEntity.ok(ApiResponse.of(data, CorrelationIdContext.get()));
    }
}

