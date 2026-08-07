package com.ddmo.app.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionInterceptorTest {

    @Test
    void knownPathsMapped() {
        assertThat(PermissionInterceptor.resolvePermission("/api/customers", "GET"))
            .isEqualTo(StaffRole.Perm.CUSTOMERS);
        assertThat(PermissionInterceptor.resolvePermission("/api/reports/dashboard", "GET"))
            .isEqualTo(StaffRole.Perm.DASHBOARD);
        assertThat(PermissionInterceptor.resolvePermission("/api/reports/summary", "GET"))
            .isEqualTo(StaffRole.Perm.REPORTS);
        assertThat(PermissionInterceptor.resolvePermission("/api/transactions/consume", "POST"))
            .isEqualTo(StaffRole.Perm.CONSUME);
    }

    @Test
    void unknownPathDenied() {
        assertThat(PermissionInterceptor.resolvePermission("/api/secret-backdoor", "GET"))
            .isEqualTo(PermissionInterceptor.DENY);
        assertThat(PermissionInterceptor.resolvePermission("/api/foo/bar", "POST"))
            .isEqualTo(PermissionInterceptor.DENY);
    }
}
