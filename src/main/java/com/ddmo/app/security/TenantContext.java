package com.ddmo.app.security;

/**
 * C 端请求上下文：租户 + 当前登录账号。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> MANAGER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException("租户上下文不存在");
        }
        return tenantId;
    }

    public static void setManagerId(Long managerId) {
        MANAGER_ID.set(managerId);
    }

    public static Long getManagerId() {
        return MANAGER_ID.get();
    }

    public static long requireManagerId() {
        Long id = MANAGER_ID.get();
        if (id == null) {
            throw new IllegalStateException("账号上下文不存在");
        }
        return id;
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static String requireUsername() {
        String u = USERNAME.get();
        if (u == null || u.isBlank()) {
            throw new IllegalStateException("账号上下文不存在");
        }
        return u;
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    public static String getRole() {
        String r = ROLE.get();
        return r == null || r.isBlank() ? StaffRole.OWNER.code() : r;
    }

    public static StaffRole getStaffRole() {
        return StaffRole.fromCode(getRole());
    }

    public static void clear() {
        TENANT_ID.remove();
        MANAGER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
    }
}
