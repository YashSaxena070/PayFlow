package com.payflow.common.tenant;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public record TenantInfo(
            UUID merchantId,
            UUID userId,
            String email,
            String role,
            boolean isApiKey) {
    }

    public static void set(UUID merchantId, UUID userId, String email, String role, boolean isApiKey) {
        CURRENT_TENANT.set(new TenantInfo(merchantId, userId, email, role, isApiKey));
    }

    public static TenantInfo get() {
        return CURRENT_TENANT.get();
    }

    public static UUID getMerchantId() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.merchantId() : null;
    }

    public static UUID getUserId() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.userId() : null;
    }

    public static String getRole() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.role() : null;
    }

    public static boolean isPlatformAdmin() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null && "PLATFORM_ADMIN".equals(info.role());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
