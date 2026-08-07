package com.ddmo.app.service;

import com.ddmo.app.security.StaffRole;
import com.ddmo.app.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * C 端角色权限校验。
 */
@Service
public class RolePermissionService {

    public Set<String> permissionsOf(StaffRole role) {
        return role.permissions();
    }

    public Set<String> currentPermissions() {
        return TenantContext.getStaffRole().permissions();
    }

    public boolean has(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return currentPermissions().contains(permission);
    }

    public void assertHas(String permission) {
        if (!has(permission)) {
            throw new IllegalArgumentException("无权限执行此操作（需要 " + permission + "）");
        }
    }

    public void assertOwner() {
        if (!TenantContext.getStaffRole().isOwner()) {
            throw new IllegalArgumentException("仅店长可执行此操作");
        }
    }

    public List<String> permissionList(StaffRole role) {
        return new ArrayList<>(role.permissions());
    }
}
