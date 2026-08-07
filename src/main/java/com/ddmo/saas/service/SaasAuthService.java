package com.ddmo.saas.service;

import com.ddmo.app.config.AppDeploymentProperties;
import com.ddmo.app.dto.LoginRequest;
import com.ddmo.app.security.JwtService;
import com.ddmo.app.util.SnowflakeIdGenerator;
import com.ddmo.saas.config.AppSaasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Order(200)
public class SaasAuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaasAuthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final SnowflakeIdGenerator idGenerator;
    private final AppSaasProperties saasProperties;
    private final AppDeploymentProperties deploymentProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SaasAuthService(
        JdbcTemplate jdbcTemplate,
        JwtService jwtService,
        SnowflakeIdGenerator idGenerator,
        AppSaasProperties saasProperties,
        AppDeploymentProperties deploymentProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.idGenerator = idGenerator;
        this.saasProperties = saasProperties;
        this.deploymentProperties = deploymentProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Local buyout SKU: skip SaaS bootstrap
        if (!deploymentProperties.isSaasEnabled()) {
            log.info("desktop deployment: skip SaaS bootstrap");
            return;
        }
        bootstrapAdmin();
        bootstrapDemoInvite();
    }

    /** If no invite codes exist, insert the configured static invite for local demo. */
    private void bootstrapDemoInvite() {
        try {
            String code = saasProperties.getInviteCode();
            if (code == null || code.isBlank()) {
                return;
            }
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_invite_code", Long.class);
            if (count != null && count > 0) {
                return;
            }
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_invite_code WHERE code = ?", Integer.class, code.trim()
            );
            if (exists != null && exists > 0) {
                return;
            }
            long id = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO t_invite_code(id, code, max_uses, used_count, status, expire_at, note, created_at, updated_at)
                    VALUES (?, ?, 9999, 0, 'active', NULL, 'demo invite (auto)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                id, code.trim()
            );
            log.info("created demo invite code: {}", code.trim());
        } catch (Exception e) {
            log.warn("demo invite bootstrap failed: {}", e.getMessage());
        }
    }

    private void bootstrapAdmin() {
        try {
            if (!saasProperties.isBootstrapEnabled()) {
                log.info("SaaS bootstrap-enabled=false, skip bootstrap admin");
                return;
            }
            String password = saasProperties.getBootstrapPassword();
            if (password == null || password.isBlank()) {
                return;
            }
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_platform_admin", Long.class);
            if (count != null && count > 0) {
                return;
            }
            String username = saasProperties.getBootstrapUsername();
            if (username == null || username.isBlank()) {
                username = "platform";
            }
            long id = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO t_platform_admin(id, username, password_hash, nickname, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'SaaS Admin', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                id, username.trim(), passwordEncoder.encode(password.trim())
            );
            log.info("created SaaS bootstrap admin: {}", username);
        } catch (Exception e) {
            log.warn("SaaS admin bootstrap failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null
            || request.getUsername().isBlank() || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, username, password_hash, nickname, status FROM t_platform_admin WHERE username = ?",
            request.getUsername().trim()
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        Map<String, Object> row = rows.get(0);
        if (!"active".equals(String.valueOf(row.get("status")))) {
            throw new IllegalArgumentException("账号已停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), String.valueOf(row.get("password_hash")))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtService.generateSaasToken(String.valueOf(row.get("username")));
        Map<String, Object> user = new HashMap<>();
        user.put("username", String.valueOf(row.get("username")));
        user.put("nickname", String.valueOf(row.get("nickname")));
        user.put("role", "saas");
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }
}
