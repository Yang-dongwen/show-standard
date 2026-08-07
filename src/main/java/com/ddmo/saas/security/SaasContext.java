package com.ddmo.saas.security;

public final class SaasContext {

    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private SaasContext() {
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        String u = USERNAME.get();
        if (u == null || u.isBlank()) {
            throw new IllegalStateException("SaaS 上下文不存在");
        }
        return u;
    }

    public static void clear() {
        USERNAME.remove();
    }
}
