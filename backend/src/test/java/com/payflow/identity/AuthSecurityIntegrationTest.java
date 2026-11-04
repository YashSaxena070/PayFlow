package com.payflow.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.seed.DataSeeder;
import com.payflow.identity.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully authenticate valid merchant admin and return JWT")
    void testSuccessfulLogin() throws Exception {
        System.out.println(">>> START testSuccessfulLogin");
        LoginRequest request = new LoginRequest(DataSeeder.ACME_ADMIN_EMAIL, DataSeeder.DEFAULT_PASSWORD, null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.email", equalTo(DataSeeder.ACME_ADMIN_EMAIL)))
                .andExpect(jsonPath("$.data.merchantName", equalTo("Acme Global Corp")))
                .andExpect(jsonPath("$.data.role", equalTo("MERCHANT_ADMIN")))
                .andExpect(jsonPath("$.meta.correlationId", notNullValue()));
        System.out.println(">>> END testSuccessfulLogin");
    }

    @Test
    @DisplayName("Should reject invalid password with 401 UNAUTHORIZED")
    void testFailedLogin() throws Exception {
        LoginRequest request = new LoginRequest(DataSeeder.ACME_ADMIN_EMAIL, "wrongpassword", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")))
                .andExpect(jsonPath("$.meta.correlationId", notNullValue()));
    }

    @Test
    @DisplayName("Should authenticate request using pre-seeded API key")
    void testApiKeyAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + DataSeeder.ACME_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].rawSecretKey", nullValue()));
    }

    @Test
    @DisplayName("Should block unauthenticated access to protected endpoints")
    void testUnauthenticatedAccessBlocked() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isForbidden());
    }
}
