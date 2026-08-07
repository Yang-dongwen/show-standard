package com.ddmo.saas.security;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.security.JwtService;
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
 * SaaS 鉴权：仅 /api/saas/**（公开登录/开店除外）。
 * 与 C 端 {@link com.ddmo.app.security.AuthFilter} 完全隔离。
 */
@Component
@Order(10)
public class SaasAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public SaasAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/saas/")) {
            return true;
        }
        return "/api/saas/auth/login".equals(path)
            || "/api/saas/public/register-shop".equals(path)
            || "/api/saas/public/register-status".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                writeUnauthorized(response, "未登录或token缺失");
                return;
            }
            String token = auth.substring(7);
            String typ = jwtService.parseTyp(token);
            if (!JwtService.TYP_SAAS.equals(typ) && !JwtService.TYP_PLATFORM.equals(typ)) {
                writeUnauthorized(response, "需要 SaaS 运营权限");
                return;
            }
            SaasContext.setUsername(jwtService.parseSubject(token));
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response, e.getMessage());
        } finally {
            SaasContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }
}
