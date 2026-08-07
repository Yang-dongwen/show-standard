package com.ddmo.app.service;

import com.ddmo.app.dto.StaffAccountRequest;
import com.ddmo.app.security.StaffRole;
import com.ddmo.app.security.TenantContext;
import com.ddmo.app.util.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店登录账号管理（店长创建收银员/店员）。
 */
@Service
public class StaffAccountService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final RolePermissionService rolePermissionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffAccountService(
        JdbcTemplate jdbcTemplate,
        SnowflakeIdGenerator idGenerator,
        RolePermissionService rolePermissionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.rolePermissionService = rolePermissionService;
    }

    public List<Map<String, Object>> list() {
        rolePermissionService.assertOwner();
        long tenantId = TenantContext.getTenantId();
        return jdbcTemplate.queryForList("""
                SELECT id, username, nickname, role, status, created_at, updated_at
                FROM t_manager
                WHERE tenant_id = ?
                ORDER BY CASE role WHEN 'owner' THEN 0 WHEN 'cashier' THEN 1 ELSE 2 END, created_at ASC
                """, tenantId
        ).stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(StaffAccountRequest request) {
        rolePermissionService.assertOwner();
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String username = trim(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String nickname = trim(request.getNickname());
        String roleCode = request.getRole() == null ? StaffRole.STAFF.code() : request.getRole().trim().toLowerCase(Locale.ROOT);

        if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            throw new IllegalArgumentException("用户名、密码、昵称不能为空");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (nickname.length() > 12) {
            throw new IllegalArgumentException("昵称最多12个字");
        }
        if (username.length() > 32) {
            throw new IllegalArgumentException("用户名过长");
        }
        if (!StaffRole.isValidAssignable(roleCode)) {
            throw new IllegalArgumentException("角色无效，可选：owner / cashier / staff");
        }
        if (StaffRole.OWNER.code().equals(roleCode)) {
            throw new IllegalArgumentException("不可创建第二个店长角色，请使用收银员或店员");
        }

        long tenantId = TenantContext.getTenantId();
        assertWithinQuota(tenantId);

        Long exist = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager WHERE username = ?", Long.class, username
        );
        if (exist != null && exist > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        long id = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_manager(id, tenant_id, username, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            id, tenantId, username, passwordEncoder.encode(password), nickname, roleCode
        );
        return getById(id);
    }

    @Transactional
    public Map<String, Object> update(long id, StaffAccountRequest request) {
        rolePermissionService.assertOwner();
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        Map<String, Object> existing = requireInTenant(id);
        String currentRole = String.valueOf(existing.get("role"));
        boolean isSelf = TenantContext.getManagerId() != null && TenantContext.getManagerId() == id;

        String nickname = request.getNickname() == null ? null : request.getNickname().trim();
        String roleCode = request.getRole() == null ? null : request.getRole().trim().toLowerCase(Locale.ROOT);
        String status = request.getStatus() == null ? null : request.getStatus().trim().toLowerCase(Locale.ROOT);

        if (nickname != null) {
            if (nickname.isEmpty()) {
                throw new IllegalArgumentException("昵称不能为空");
            }
            if (nickname.length() > 12) {
                throw new IllegalArgumentException("昵称最多12个字");
            }
        }
        if (roleCode != null) {
            if (!StaffRole.isValidAssignable(roleCode)) {
                throw new IllegalArgumentException("角色无效");
            }
            if (StaffRole.OWNER.code().equals(currentRole) && !StaffRole.OWNER.code().equals(roleCode)) {
                throw new IllegalArgumentException("不能降级唯一店长账号的角色");
            }
            if (!StaffRole.OWNER.code().equals(currentRole) && StaffRole.OWNER.code().equals(roleCode)) {
                throw new IllegalArgumentException("不可将账号提升为店长（每店仅一名店长）");
            }
        }
        if (status != null) {
            if (!"active".equals(status) && !"disabled".equals(status)) {
                throw new IllegalArgumentException("状态仅支持 active / disabled");
            }
            if (StaffRole.OWNER.code().equals(currentRole) && "disabled".equals(status)) {
                throw new IllegalArgumentException("不能停用店长账号");
            }
            if (isSelf && "disabled".equals(status)) {
                throw new IllegalArgumentException("不能停用当前登录账号");
            }
        }

        if (nickname != null) {
            jdbcTemplate.update(
                "UPDATE t_manager SET nickname = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                nickname, id
            );
        }
        if (roleCode != null && !StaffRole.OWNER.code().equals(currentRole)) {
            jdbcTemplate.update(
                "UPDATE t_manager SET role = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                roleCode, id
            );
        }
        if (status != null) {
            jdbcTemplate.update(
                "UPDATE t_manager SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, id
            );
        }
        return getById(id);
    }

    @Transactional
    public void resetPassword(long id, String newPassword) {
        rolePermissionService.assertOwner();
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("新密码至少6位");
        }
        requireInTenant(id);
        jdbcTemplate.update(
            "UPDATE t_manager SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            passwordEncoder.encode(newPassword.trim()), id
        );
    }

    @Transactional
    public Map<String, Object> toggleStatus(long id) {
        rolePermissionService.assertOwner();
        Map<String, Object> existing = requireInTenant(id);
        String currentRole = String.valueOf(existing.get("role"));
        String status = String.valueOf(existing.get("status"));
        if (StaffRole.OWNER.code().equals(currentRole)) {
            throw new IllegalArgumentException("不能停用店长账号");
        }
        if (TenantContext.getManagerId() != null && TenantContext.getManagerId() == id) {
            throw new IllegalArgumentException("不能停用当前登录账号");
        }
        String next = "active".equals(status) ? "disabled" : "active";
        jdbcTemplate.update(
            "UPDATE t_manager SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            next, id
        );
        return getById(id);
    }

    public Map<String, Object> rolesCatalog() {
        Map<String, Object> m = new HashMap<>();
        m.put("roles", List.of(
            roleInfo(StaffRole.OWNER),
            roleInfo(StaffRole.CASHIER),
            roleInfo(StaffRole.STAFF)
        ));
        return m;
    }

    private Map<String, Object> roleInfo(StaffRole role) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", role.code());
        m.put("label", role.label());
        m.put("permissions", rolePermissionService.permissionList(role));
        return m;
    }

    private void assertWithinQuota(long tenantId) {
        Integer max = jdbcTemplate.queryForObject(
            "SELECT max_employees FROM t_tenant WHERE id = ?", Integer.class, tenantId
        );
        int limit = max == null ? 50 : max;
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager WHERE tenant_id = ?", Long.class, tenantId
        );
        long n = count == null ? 0 : count;
        if (n >= limit) {
            throw new IllegalArgumentException("登录账号数已达套餐上限（" + limit + "）");
        }
    }

    private Map<String, Object> requireInTenant(long id) {
        long tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, username, nickname, role, status FROM t_manager WHERE id = ? AND tenant_id = ?",
            id, tenantId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("账号不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getById(long id) {
        long tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, username, nickname, role, status, created_at, updated_at
                FROM t_manager WHERE id = ? AND tenant_id = ?
                """, id, tenantId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("账号不存在");
        }
        return toView(rows.get(0));
    }

    private Map<String, Object> toView(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("username", String.valueOf(row.get("username")));
        m.put("nickname", String.valueOf(row.get("nickname")));
        StaffRole role = StaffRole.fromCode(String.valueOf(row.get("role")));
        m.put("role", role.code());
        m.put("roleLabel", role.label());
        m.put("status", String.valueOf(row.get("status")));
        if (row.get("created_at") != null) {
            m.put("createdAt", row.get("created_at").toString());
        }
        if (row.get("updated_at") != null) {
            m.put("updatedAt", row.get("updated_at").toString());
        }
        return m;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
