package com.payflow.identity;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.identity.controller.ApiKeyController;
import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.domain.UserRole;
import com.payflow.identity.dto.CreateApiKeyRequest;
import com.payflow.identity.service.ApiKeyService;
import com.payflow.merchant.domain.Merchant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantSecurityUnitTest {

    private ApiKeyServiceUnitTest.FakeApiCredentialRepository credentialRepo;
    private ApiKeyServiceUnitTest.FakeMerchantRepository merchantRepo;
    private ApiKeyService apiKeyService;
    private ApiKeyController apiKeyController;

    private final UUID merchantA = UUID.randomUUID();
    private final UUID merchantB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        credentialRepo = new ApiKeyServiceUnitTest.FakeApiCredentialRepository();
        merchantRepo = new ApiKeyServiceUnitTest.FakeMerchantRepository();
        merchantRepo.save(new Merchant(merchantA, "Merchant Alpha", "ACTIVE"));
        merchantRepo.save(new Merchant(merchantB, "Merchant Beta", "ACTIVE"));

        apiKeyService = new ApiKeyService(credentialRepo, merchantRepo);
        apiKeyController = new ApiKeyController(apiKeyService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("AC-03: Merchant A cannot view or list Merchant B's API keys")
    void testTenantIsolationOnList() {
        // Create key for Merchant A
        TenantContext.set(merchantA, UUID.randomUUID(), "admin@alpha.com", UserRole.MERCHANT_ADMIN.name(), false);
        apiKeyController.createApiKey(new CreateApiKeyRequest("payments:write", null));

        // Create key for Merchant B
        TenantContext.set(merchantB, UUID.randomUUID(), "admin@beta.com", UserRole.MERCHANT_ADMIN.name(), false);
        apiKeyController.createApiKey(new CreateApiKeyRequest("payments:read", null));

        // Switch context to Merchant A: must only see Merchant A's key
        TenantContext.set(merchantA, UUID.randomUUID(), "admin@alpha.com", UserRole.MERCHANT_ADMIN.name(), false);
        var responseA = apiKeyController.listApiKeys();
        assertEquals(1, responseA.getBody().data().size());
        assertEquals("payments:write", responseA.getBody().data().get(0).scopes());

        // Switch context to Merchant B: must only see Merchant B's key
        TenantContext.set(merchantB, UUID.randomUUID(), "admin@beta.com", UserRole.MERCHANT_ADMIN.name(), false);
        var responseB = apiKeyController.listApiKeys();
        assertEquals(1, responseB.getBody().data().size());
        assertEquals("payments:read", responseB.getBody().data().get(0).scopes());
    }

    @Test
    @DisplayName("AC-03: Merchant A cannot revoke Merchant B's API key")
    void testCrossTenantRevocationBlocked() {
        // Merchant B creates an API key
        TenantContext.set(merchantB, UUID.randomUUID(), "admin@beta.com", UserRole.MERCHANT_ADMIN.name(), false);
        var createdB = apiKeyController.createApiKey(new CreateApiKeyRequest("payments:read", null)).getBody().data();

        // Merchant A attempts to revoke Merchant B's key
        TenantContext.set(merchantA, UUID.randomUUID(), "attacker@alpha.com", UserRole.MERCHANT_ADMIN.name(), false);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                apiKeyController.revokeApiKey(createdB.id()));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Read-only analyst cannot generate new API keys")
    void testRoleBasedPermissions() {
        TenantContext.set(merchantA, UUID.randomUUID(), "analyst@alpha.com", UserRole.READ_ONLY_ANALYST.name(), false);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                apiKeyController.createApiKey(new CreateApiKeyRequest("payments:write", null)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }
}
