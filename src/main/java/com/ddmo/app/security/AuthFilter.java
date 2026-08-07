package com.ddmo.app.security;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.service.TenantAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * C 端鉴权：仅处理 /api/**（排除 /api/saas/**）。
 * 与 SaaS {@code SaasAuthFilter} 完全隔离。
 * <p>
 * 每个请求校验租户可登录；写方法再校验可写（停用/到期/只读即时生效）。
 */
@Component
@Order(20)
public class AuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final TenantAccessService tenantAccessService;

    public AuthFilter(JwtService jwtService, ObjectMapper objectMapper, TenantAccessService tenantAccessService) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.tenantAccessService = tenantAccessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/saas/")) {
            return true;
        }
        return !path.startsWith("/api/")
            || path.startsWith("/api/install/")
            || "/api/auth/login".equals(path)
            || "/api/auth/register".equals(path)
            || "/api/auth/register-status".equals(path)
            || "/api/auth/wx-login".equals(path)
            || "/api/auth/wx-bind".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或token缺失");
                return;
            }
            String token = auth.substring(7);
            String typ = jwtService.parseTyp(token);
            if (JwtService.TYP_SAAS.equals(typ) || JwtService.TYP_PLATFORM.equals(typ)) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "SaaS 令牌不能访问 C 端接口");
                return;
            }
            long tenantId = jwtService.parseTenantId(token);
            // 停用/到期：立即拒绝（不再等到 token 过期）
            try {
                tenantAccessService.assertCanLogin(tenantId);
            } catch (IllegalArgumentException ex) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
                return;
            }
            // 只读/停用/到期：禁止业务写入
            if (isWriteMethod(request.getMethod())) {
                try {
                    tenantAccessService.assertCanWrite(tenantId);
                } catch (IllegalArgumentException ex) {
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
                    return;
                }
            }
            TenantContext.setTenantId(tenantId);
            TenantContext.setUsername(jwtService.parseSubject(token));
            TenantContext.setRole(jwtService.parseRole(token));
            Long mid = jwtService.parseManagerId(token);
            if (mid != null) {
                TenantContext.setManagerId(mid);
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase();
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }
}
