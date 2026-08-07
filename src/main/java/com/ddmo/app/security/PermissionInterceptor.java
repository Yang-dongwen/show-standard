package com.ddmo.app.security;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.service.RolePermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 按路径+方法校验 C 端角色权限。
 * <p>
 * <b>fail-closed</b>：未映射到权限码的路径一律拒绝（白名单除外）。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    /** 显式放行（登录后即可，不要求业务权限码） */
    static final String ALLOW = null;
    /** 未映射路径：拒绝 */
    static final String DENY = "__deny__";

    private final RolePermissionService rolePermissionService;
    private final ObjectMapper objectMapper;

    public PermissionInterceptor(RolePermissionService rolePermissionService, ObjectMapper objectMapper) {
        this.rolePermissionService = rolePermissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/saas/")) {
            return true;
        }
        // 公开鉴权与安装接口由 AuthFilter 处理白名单
        if (path.startsWith("/api/auth/") || path.startsWith("/api/install/")) {
            return true;
        }
        if (path.startsWith("/api/system/product-line") || path.startsWith("/api/system/access-info")
            || path.equals("/api/system/info")) {
            return true;
        }

        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
        String required = resolvePermission(path, method);
        if (required == null) {
            return true;
        }
        if (DENY.equals(required)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail("无权限访问（未授权的接口路径）")
            ));
            return false;
        }
        if (rolePermissionService.has(required)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            ApiResponse.fail("无权限访问（需要 " + required + "）")
        ));
        return false;
    }

    /**
     * @return 需要的权限码；{@link #ALLOW} 不限制；{@link #DENY} 拒绝
     */
    static String resolvePermission(String path, String method) {
        boolean write = "POST".equals(method) || "PUT".equals(method)
            || "PATCH".equals(method) || "DELETE".equals(method);

        if (path.startsWith("/api/staff")) {
            return StaffRole.Perm.STAFF_ACCOUNTS;
        }
        if (path.startsWith("/api/audit")) {
            return StaffRole.Perm.AUDIT;
        }
        // 经营总览：dashboard 权限即可；其它报表要 reports
        if (path.startsWith("/api/reports/dashboard")) {
            return StaffRole.Perm.DASHBOARD;
        }
        if (path.startsWith("/api/reports") || path.startsWith("/api/export")) {
            return StaffRole.Perm.REPORTS;
        }
        if (path.startsWith("/api/employees")) {
            if (!write) {
                return StaffRole.Perm.TRANSACTIONS;
            }
            return StaffRole.Perm.EMPLOYEES;
        }
        if (path.startsWith("/api/config/services") || path.startsWith("/api/service-types")) {
            if (!write) {
                return StaffRole.Perm.TRANSACTIONS;
            }
            return StaffRole.Perm.SETTINGS;
        }
        if (path.startsWith("/api/settings") || path.startsWith("/api/shop")) {
            return StaffRole.Perm.SETTINGS;
        }
        if (path.startsWith("/api/system/backup")) {
            return StaffRole.Perm.BACKUP;
        }
        if (path.startsWith("/api/announcements")) {
            return StaffRole.Perm.DASHBOARD;
        }
        if (path.startsWith("/api/customers")) {
            if (write) {
                return StaffRole.Perm.CUSTOMERS_WRITE;
            }
            return StaffRole.Perm.CUSTOMERS;
        }
        if (path.startsWith("/api/transactions")) {
            if (path.contains("/reverse")) {
                return StaffRole.Perm.REVERSE;
            }
            if (path.endsWith("/recharge") && "POST".equals(method)) {
                return StaffRole.Perm.RECHARGE;
            }
            if (path.endsWith("/consume") && "POST".equals(method)) {
                return StaffRole.Perm.CONSUME;
            }
            return StaffRole.Perm.TRANSACTIONS;
        }
        if (path.startsWith("/api/accounts")) {
            return StaffRole.Perm.CUSTOMERS;
        }
        // 未在上表声明的 /api/** → 拒绝（fail-closed）
        return DENY;
    }
}
