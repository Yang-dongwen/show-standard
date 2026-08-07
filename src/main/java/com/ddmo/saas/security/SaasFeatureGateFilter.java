package com.ddmo.saas.security;

import com.ddmo.app.config.AppDeploymentProperties;
import com.ddmo.app.dto.ApiResponse;
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
import java.nio.charset.StandardCharsets;

/**
 * 纯本地买断版（desktop）关闭 SaaS 运营 API 与 /saas/ 入口。
 * SaaS 仅 cloud 部署（云端 MySQL）可用。
 */
@Component
@Order(5)
public class SaasFeatureGateFilter extends OncePerRequestFilter {

    private final AppDeploymentProperties deploymentProperties;
    private final ObjectMapper objectMapper;

    public SaasFeatureGateFilter(AppDeploymentProperties deploymentProperties, ObjectMapper objectMapper) {
        this.deploymentProperties = deploymentProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (deploymentProperties.isSaasEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/saas") || path.startsWith("/saas"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/saas")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail("当前为本地买断版，不包含 SaaS 运营能力；请使用 SaaS 云版（云端 MySQL）")
            ));
            return;
        }
        // /saas 静态页
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
            <!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"/><title>本地版</title></head>
            <body style="font-family:sans-serif;padding:2rem;max-width:40rem">
            <h1>本地买断版</h1>
            <p>本安装包为<strong>纯本地</strong>产品：数据存本机 SQLite，不包含 SaaS 运营台与微信小程序。</p>
            <p>若需要多店开通、运营中台或商家小程序，请使用 <strong>SaaS 云版</strong>（云端 MySQL 部署）。</p>
            <p><a href="/">返回门店系统</a></p>
            </body></html>
            """);
    }
}
